package nl.tudelft.trustchain.offlineeuro.entity

import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProof
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProofBytes
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer

enum class TransactionResult(val valid: Boolean, val description: String) {
    VALID_TRANSACTION(true, "Valid transaction"),
    INVALID_BANK_SIGNATURE(false, "Invalid bank signature"),
    INVALID_TRANSACTION_SIGNATURE(false, "Invalid transaction signature"),
    INVALID_CURRENT_TRANSACTION_PROOF(false, "Invalid current transaction proof"),
    INVALID_YS_GIVEN(false, "Invalid YS given"),
    INVALID_CURRENT_TARGET(false, "Invalid target in current proof"),
    INVALID_TS_RELATION_BANK_SIGNATURE(false, "Invalid TS relation bank signature"),
    INVALID_PROOF_IN_CHAIN(false, "Invalid proof in chain"),
    INVALID_CHAIN_OF_PROOFS(false, "Invalid chaining of proofs"),
    INVALID_PREVIOUS_TRANSACTION_SIGNATURE(false, "Invalid previous transaction signature"),
}

data class TransactionDetailsBytes(
    val digitalEuroBytes: DigitalEuroBytes,
    val currentTransactionProofBytes: TransactionProofBytes,
    val previousThetaSignatureBytes: ByteArray,
    val theta1SignatureBytes: ByteArray,
    val spenderPublicKeyBytes: ByteArray,
) {
    fun toTransactionDetails(group: BilinearGroup): TransactionDetails {
        return TransactionDetails(
            digitalEuroBytes.toDigitalEuro(group),
            currentTransactionProofBytes.toTransactionProof(group),
            SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(previousThetaSignatureBytes),
            SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(theta1SignatureBytes)!!,
            group.gElementFromBytes(spenderPublicKeyBytes)
        )
    }
}

data class TransactionDetails(
    val digitalEuro: DigitalEuro,
    val currentTransactionProof: TransactionProof,
    val previousThetaSignature: SchnorrSignature?,
    val theta1Signature: SchnorrSignature,
    val spenderPublicKey: Element,
) {
    fun toTransactionDetailsBytes(): TransactionDetailsBytes {
        return TransactionDetailsBytes(
            digitalEuro.toDigitalEuroBytes(),
            currentTransactionProof.toTransactionProofBytes(),
            SchnorrSignatureSerializer.serializeSchnorrSignature(previousThetaSignature),
            SchnorrSignatureSerializer.serializeSchnorrSignature(theta1Signature),
            spenderPublicKey.toBytes()
        )
    }
}

object Transaction {
    fun createTransaction(
        privateKey: Element,
        publicKey: Element,
        walletEntry: WalletEntry,
        randomizationElements: RandomizationElements,
        bilinearGroup: BilinearGroup,
        crs: CRS
    ): TransactionDetails {
        val digitalEuro = walletEntry.digitalEuro

        val target =
            if (digitalEuro.proofs.isEmpty()) {
                bilinearGroup.pairing.zr.newElementFromBytes(digitalEuro.signature.toBytes())
            } else {
                val targetBytes = digitalEuro.proofs.last().target.toBytes()
                bilinearGroup.pairing.zr.newElementFromBytes(targetBytes)
            }
        val (transactionProof, r) =
            GrothSahai.createTransactionProof(
                privateKey,
                publicKey,
                target,
                walletEntry.t,
                randomizationElements,
                bilinearGroup,
                crs
            )

        val transactionProofSize = GrothSahaiSerializer.serializeGrothSahaiProof(transactionProof.grothSahaiProof).size
        val theta1Signature = Schnorr.schnorrSignature(r, randomizationElements.group1TInv.toBytes(), bilinearGroup)
        val previousThetaSignature = walletEntry.transactionSignature
        return TransactionDetails(digitalEuro, transactionProof, previousThetaSignature, theta1Signature, publicKey)
    }

    fun validate(
        transaction: TransactionDetails,
        publicKeyBank: Element,
        bilinearGroup: BilinearGroup,
        crs: CRS
    ): TransactionResult {
        // Verify if the Digital euro is signed

        val digitalEuro = transaction.digitalEuro
        if (!digitalEuro.verifySignature(publicKeyBank, bilinearGroup) ||
            !digitalEuro.signature.signedMessage.contentEquals(digitalEuro.serialNumber.toByteArray() + digitalEuro.firstTheta1.toBytes())
        ) {
            return TransactionResult.INVALID_BANK_SIGNATURE
        }

        // Verify if the current transaction signature is correct
        val transactionProof = transaction.currentTransactionProof

        val transactionSignature = transaction.theta1Signature
        if (!Schnorr.verifySchnorrSignature(transactionSignature, transactionProof.grothSahaiProof.c1, bilinearGroup)) {
            return TransactionResult.INVALID_TRANSACTION_SIGNATURE
        }

        // Validate if the given Transaction proof is a valid Groth-Sahai proof
        if (!GrothSahai.verifyTransactionProof(transactionProof.grothSahaiProof, bilinearGroup, crs)) {
            return TransactionResult.INVALID_CURRENT_TRANSACTION_PROOF
        }

        // Validate that d2 is constructed correctly
        val usedY = transactionProof.usedY
        val usedVS = transactionProof.usedVS
        val d2 = transactionProof.grothSahaiProof.d2

        if (d2.div(usedY) != usedVS) {
            return TransactionResult.INVALID_YS_GIVEN
        }

        // Validate if the public key is used correctly
        val spenderPublicKey = transaction.spenderPublicKey
        val expectedTarget = bilinearGroup.pair(spenderPublicKey, usedY)

        if (expectedTarget != transactionProof.grothSahaiProof.target) {
            return TransactionResult.INVALID_CURRENT_TARGET
        }

        return validateProofChain(transaction, bilinearGroup, crs)
    }

    fun validateProofChain(
        currentTransactionDetails: TransactionDetails,
        bilinearGroup: BilinearGroup,
        crs: CRS
    ): TransactionResult {
        val digitalEuro = currentTransactionDetails.digitalEuro
        val currentProof = currentTransactionDetails.currentTransactionProof
        val previousProofs = digitalEuro.proofs

        // Current proof is the first proof
        if (previousProofs.isEmpty()) {
            return if (!validateTSRelation(digitalEuro.firstTheta1, currentProof.grothSahaiProof.d1, bilinearGroup)) {
                TransactionResult.INVALID_TS_RELATION_BANK_SIGNATURE
            } else {
                TransactionResult.VALID_TRANSACTION
            }
        }

        // Special cases for the first proof
        val firstProof = previousProofs.first()

        if (!validateTSRelation(
                digitalEuro.firstTheta1,
                firstProof.d1,
                bilinearGroup
            )
        ) {
            return TransactionResult.INVALID_TS_RELATION_BANK_SIGNATURE
        }

        if (!GrothSahai.verifyTransactionProof(firstProof, bilinearGroup, crs)) {
            return TransactionResult.INVALID_PROOF_IN_CHAIN
        }

        // Verify the remainder of the chain
        var previousProof = firstProof
        for (i: Int in 1 until previousProofs.size) {
            val proof = previousProofs[i]
            if (!GrothSahai.verifyTransactionProof(proof, bilinearGroup, crs)) {
                return TransactionResult.INVALID_PROOF_IN_CHAIN
            }

            if (!verifyProofChain(previousProof, proof, bilinearGroup)) {
                return TransactionResult.INVALID_CHAIN_OF_PROOFS
            }
            previousProof = proof
        }

        // Verify if the signature of the last transaction is valid
        val usedC1 = previousProof.c1
        val usedTheta = previousProof.theta1

        val previousTransactionProof = currentTransactionDetails.previousThetaSignature
        if (previousTransactionProof == null ||
            !Schnorr.verifySchnorrSignature(previousTransactionProof, usedC1, bilinearGroup) ||
            !usedTheta.toBytes().contentEquals(previousTransactionProof.signedMessage)
        ) {
            return TransactionResult.INVALID_PREVIOUS_TRANSACTION_SIGNATURE
        }
        // Verify the proof of the transaction to the last proof in the chain
        return if (verifyProofChain(previousProof, currentProof.grothSahaiProof, bilinearGroup)) {
            TransactionResult.VALID_TRANSACTION
        } else {
            TransactionResult.INVALID_CHAIN_OF_PROOFS
        }
    }

    fun verifyProofChain(
        previousProof: GrothSahaiProof,
        currentProof: GrothSahaiProof,
        bilinearGroup: BilinearGroup,
    ): Boolean {
        val g = bilinearGroup.g
        val h = bilinearGroup.h
        val previousTargetToBytes = previousProof.target.toCanonicalRepresentation()
        val previousTargetAsPower = bilinearGroup.pairing.zr.newElementFromBytes(previousTargetToBytes)
        val expectedTarget = bilinearGroup.pair(g, h).powZn(previousTargetAsPower)

        if (expectedTarget != currentProof.target) {
            return false
        }

        // Check if the relation with t and s is correct
        val previousTheta1 = previousProof.theta1
        val currentD1 = currentProof.d1
        return validateTSRelation(previousTheta1, currentD1, bilinearGroup)
    }

    fun validateTSRelation(
        theta: Element,
        s: Element,
        bilinearGroup: BilinearGroup
    ): Boolean {
        val g = bilinearGroup.g
        val h = bilinearGroup.h

        val expectedPairing = bilinearGroup.pair(g, h)
        val actualPairing = bilinearGroup.pair(theta, s)
        return expectedPairing == actualPairing
    }
}

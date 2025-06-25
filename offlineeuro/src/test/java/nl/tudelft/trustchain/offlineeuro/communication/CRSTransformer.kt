package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes


class CRSTransformer {
    companion object {
        fun crsValues(crsMap: Map<Element, Element>): CRSBytes {
            val elems = crsMap.values

            return CRS(
                g = elems.elementAt(0),
                u = elems.elementAt(1),
                gPrime = elems.elementAt(2),
                uPrime = elems.elementAt(3),
                h = elems.elementAt(4),
                v = elems.elementAt(5),
                hPrime = elems.elementAt(6),
                vPrime = elems.elementAt(7)).toCRSBytes()
        }
    }
}

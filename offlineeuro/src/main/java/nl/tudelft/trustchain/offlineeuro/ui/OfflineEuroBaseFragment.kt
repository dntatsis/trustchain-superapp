package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import mu.KotlinLogging
import nl.tudelft.trustchain.common.ui.BaseFragment

open class OfflineEuroBaseFragment(contentLayoutId: Int = 0) : BaseFragment(contentLayoutId) {
    protected val logger = KotlinLogging.logger {}
    var isAllRoles = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenResumed {
        }
    }
}

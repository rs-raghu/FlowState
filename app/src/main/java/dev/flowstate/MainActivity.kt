package dev.flowstate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.flowstate.ui.*

class MainActivity : ComponentActivity() {
    private val vm by lazy { ViewModelProvider(this)[FlowViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { FlowState(vm,intent.getStringExtra("execution")) }
    }
    override fun onResume() { super.onResume(); vm.reconcile() }
}

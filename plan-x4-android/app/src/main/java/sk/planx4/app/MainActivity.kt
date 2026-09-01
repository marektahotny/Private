package sk.planx4.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import sk.planx4.app.ui.PlanX4NavHost
import sk.planx4.app.ui.theme.PlanX4Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlanX4Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlanX4NavHost()
                }
            }
        }
    }
}

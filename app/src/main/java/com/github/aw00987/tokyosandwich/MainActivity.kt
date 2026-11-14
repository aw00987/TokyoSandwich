package com.github.aw00987.tokyosandwich

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aw00987.tokyosandwich.cost.ui.CostViewModel
import com.github.aw00987.tokyosandwich.electricity.ui.ElectricityViewModel
import com.github.aw00987.tokyosandwich.gas.ui.GasViewModel
import com.github.aw00987.tokyosandwich.home.ui.HomeViewModel
import com.github.aw00987.tokyosandwich.ui.AppRoot
import com.github.aw00987.tokyosandwich.ui.BackupViewModel
import com.github.aw00987.tokyosandwich.ui.theme.TokyoSandwichTheme
import com.github.aw00987.tokyosandwich.water.ui.WaterViewModel

class MainActivity : ComponentActivity() {
    private val app get() = application as TokyoSandwichApp

    private val waterViewModel: WaterViewModel by viewModels {
        viewModelFactory { initializer { WaterViewModel(app.waterRepository) } }
    }

    private val electricityViewModel: ElectricityViewModel by viewModels {
        viewModelFactory { initializer { ElectricityViewModel(app.electricityRepository) } }
    }

    private val gasViewModel: GasViewModel by viewModels {
        viewModelFactory { initializer { GasViewModel(app.gasRepository) } }
    }

    private val backupViewModel: BackupViewModel by viewModels {
        viewModelFactory { initializer { BackupViewModel(app.backup, contentResolver) } }
    }

    private val costViewModel: CostViewModel by viewModels {
        viewModelFactory { initializer { CostViewModel(app.ledger, createSavedStateHandle()) } }
    }

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(app.ledger, app.settings, app.monthTotals, createSavedStateHandle())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokyoSandwichTheme {
                AppRoot(
                    waterViewModel,
                    electricityViewModel,
                    gasViewModel,
                    costViewModel,
                    homeViewModel,
                    backupViewModel,
                )
            }
        }
    }
}

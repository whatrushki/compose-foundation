package app.what.navigation.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.what.foundation.ui.controllers.DialogController
import app.what.foundation.ui.controllers.LocalDialogController
import app.what.foundation.ui.controllers.LocalSheetController
import app.what.foundation.ui.controllers.SheetController
import app.what.foundation.ui.controllers.rememberDialogHostController
import app.what.foundation.ui.controllers.rememberSheetHostController
import app.what.foundation.utils.isDesktop

/**
 * Единый корневой инициализатор глобальной навигации.
 *
 * Устраняет необходимость вложенной «матрёшки» (ProvideGlobalDialog -> ProvideGlobalSheet -> ...)
 * и освобождает дочерние экраны от ручного создания NavSheetHost / NavDialogHost.
 *
 * Предоставляет в CompositionLocal:
 * - [LocalIsWideScreen]: флаг широкого экрана (десктоп, планшет или альбомный режим)
 * - [LocalSheetNavigator]: адаптивный навигатор шторок (SideSheet на широких экранах, BottomSheet на узких)
 * - [LocalDialogNavigator]: глобальный навигатор модальных диалогов
 * - [LocalDrawerNavigator]: глобальный навигатор боковой панели
 * - [LocalSheetController] / [LocalDialogController]: поддержка legacy-контроллеров
 *
 * Любой дочерний Composable в приложении может напрямую вызывать:
 * ```kotlin
 * val sheet = rememberSheetNavigator()
 * sheet.open { MySheetContent() }
 *
 * val dialog = rememberDialogNavigator()
 * dialog.open { MyDialogContent() }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvideGlobalNavigation(
    isWideScreen: Boolean? = null,
    sheetNavigator: AppNavigator = rememberAppNavigator(),
    dialogNavigator: AppNavigator = rememberAppNavigator(),
    drawerNavigator: AppNavigator = rememberAppNavigator(),
    legacySheetController: SheetController = rememberSheetHostController(),
    legacyDialogController: DialogController = rememberDialogHostController(),
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 480.dp
        val wide = isWideScreen ?: (isDesktop || maxWidth >= 760.dp || isLandscape)

        CompositionLocalProvider(
            LocalIsWideScreen provides wide,
            LocalSheetNavigator provides sheetNavigator,
            LocalDialogNavigator provides dialogNavigator,
            LocalDrawerNavigator provides drawerNavigator,
            LocalSheetController provides legacySheetController,
            LocalDialogController provides legacyDialogController
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Основной экран / стек приложения
                content()

                // Глобальный адаптивный Sheet:
                // - На широком экране (планшет / десктоп): модальный SideSheet справа
                // - На смартфоне: ModalBottomSheet снизу
                if (wide) {
                    NavSideSheetHost(navigator = sheetNavigator)
                } else {
                    NavSheetHost(navigator = sheetNavigator)
                }

                // Глобальный модальный диалог
                NavDialogHost(navigator = dialogNavigator)
            }
        }
    }
}

package com.irfan.prosantinibash.navigation

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.irfan.prosantinibash.*
import com.irfan.prosantinibash.core.settings.AppPreferences
import com.irfan.prosantinibash.core.ui.*
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.building.*
import com.irfan.prosantinibash.feature.deposits.*
import com.irfan.prosantinibash.feature.documents.*
import com.irfan.prosantinibash.feature.expenses.*
import com.irfan.prosantinibash.feature.maintenance.*
import com.irfan.prosantinibash.feature.payments.*
import com.irfan.prosantinibash.feature.reports.*
import com.irfan.prosantinibash.feature.tenants.*
import com.irfan.prosantinibash.feature.lock.LockViewModel
import com.irfan.prosantinibash.feature.settings.*

private enum class Tab(val route:String,val title:String,val icon:Int) {
    HOME("home","Home",R.drawable.ic_home),FLATS("flats","Flats",R.drawable.ic_flats),
    PAYMENTS("payments","Payments",R.drawable.ic_payments),EXPENSES("expenses","Expenses",R.drawable.ic_expenses),MORE("more","More",R.drawable.ic_more)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainNavigation(container:AppContainer,controller:NavHostController,lock:LockViewModel,
    preferences:AppPreferences,biometricAvailable:Boolean,securityError:String?,onEnableBiometric:(CharArray)->Unit,onDisableBiometric:()->Unit) {
    val model:BookViewModel=viewModel(factory=viewModelFactory { initializer { BookViewModel(container::loadBook,container::executeBook,createSavedStateHandle(),container::cachedBook,container::commitBook,container::receipt) } })
    // NavHost remembers destinations; each destination must read the current state holder.
    val bookState = model.uiState.collectAsStateWithLifecycle()
    val state = bookState.value
    val currentPreferences = rememberUpdatedState(preferences)
    val currentSecurityError = rememberUpdatedState(securityError)
    val currentBiometricAvailable = rememberUpdatedState(biometricAvailable)
    val entry by controller.currentBackStackEntryAsState();val route=entry?.destination?.route ?: "home"
    LaunchedEffect(model) { model.ensureLoaded() }
    Scaffold(topBar={ BookAppBar(route,state,model,controller,container.session::lock) },
        bottomBar={ if(Tab.entries.any { it.route==route }) BottomTabs(controller,route) }) { padding ->
        NavHost(controller,"home",Modifier.padding(padding).consumeWindowInsets(padding)) {
            mainRoutes(bookState,model,controller,container)
            rentRoutes(bookState,model,controller)
            ledgerRoutes(bookState,model,controller)
            fileRoutes(bookState,model,controller,container)
            composable("settings") { SettingsScreen(container.preferences,currentPreferences.value,container.session::lock,lock::changePin,
                onEnableBiometric,onDisableBiometric,currentBiometricAvailable.value,currentSecurityError.value) }
            composable("reminders") { RemindersScreen(container.preferences,currentPreferences.value) }
        }
    }
}
internal fun NavGraphBuilder.mainRoutes(bookState:State<BookUiState>,model:BookViewModel,nav:NavHostController,container:AppContainer?=null) {
    composable("home") { val state=bookState.value; HomeScreen(state,model,{ nav.navigate("flat/$it") },{ nav.navigate("payment-select") },{ nav.navigate("expense-new") },{ nav.navigate("outstanding") }) }
    composable("flats") { val state=bookState.value; BookFlatsScreen(state,model) { nav.navigate("flat/$it") } }
    composable("payments") { val state=bookState.value; PaymentsScreen(state,model,{ nav.navigate("payment-new/$it") },{ nav.navigate("payment-detail/$it") }, { nav.navigate("payment-select") }, { nav.navigate("flats") }) }
    composable("expenses") { val state=bookState.value; ExpensesScreen(state,model,{ id -> nav.navigate(if(id==null) "expense-new" else "expense-edit/$id") },{ nav.navigate(fileRoute(it)) }) }
    composable("more") { val state=bookState.value; MoreScreen { nav.navigate(it) } }
    composable("outstanding") { val state=bookState.value; OutstandingScreen(state,{ nav.navigate("tenant/$it") },{ nav.navigate("payment-new/$it") },model::refresh) }
    composable("flat/{id}") { entry -> val state=bookState.value; Loaded(state,model) { val id=entry.id()
        FlatDetailsScreen(state,model,id,{ nav.navigate("tenant/$it") },{ nav.navigate("tenant-new/$id") },{ nav.navigate("payment-new/$it") },{ nav.navigate("maintenance/$id") }) } }
    composable("reports") { val state=bookState.value; ReportsScreen(state,model,{ nav.navigate("export/$it") },{ nav.navigate("tenant/$it") }) }
    composable("search") { val state=bookState.value; SearchScreen(state,{ nav.navigate("tenant/$it") },{ nav.navigate("payment-detail/$it") },{ nav.navigate("expense-edit/$it") },model::refresh) }
    composable("audit") {
        val historyModel: AuditViewModel = viewModel(factory = viewModelFactory { initializer { AuditViewModel(requireNotNull(container).bookRepository) } })
        val history by historyModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(historyModel) { historyModel.load() }
        AuditScreen(bookState.value, history, historyModel)
    }
    composable("building") { val state=bookState.value; Loaded(state,model) { BuildingScreen(state,model) } }
}
private fun NavGraphBuilder.rentRoutes(bookState:State<BookUiState>,model:BookViewModel,nav:NavHostController) {
    composable("tenant-new/{id}") { entry -> val state=bookState.value; Loaded(state,model) { TenancyFormScreen(state,model,entry.id(),onDone={ nav.popBackStack() }) } }
    composable("tenant-edit/{id}") { entry -> val state=bookState.value; Loaded(state,model) {
        val tenancy=state.book.tenancies.firstOrNull { it.id==entry.id() }
        if(tenancy==null) Text(AppText.get(R.string.tenancy_not_found)) else TenancyFormScreen(state,model,tenancy.flat_id,tenancy.id) { nav.popBackStack() }
    } }
    composable("tenant/{id}") { entry -> val state=bookState.value; Loaded(state,model) { TenantDetailsScreen(state,model,entry.id(),
        { nav.navigate("payment-new/$it") },{ nav.navigate("files/tenants/$it") },{ nav.navigate("deposit/$it") },
        { nav.navigate("move-out/$it") },{ nav.navigate("tenant-edit/$it") },{ nav.navigate("ledger/$it") }) } }
    composable("ledger/{id}") { entry -> val state=bookState.value; Loaded(state,model) { LedgerScreen(state,model,entry.id(),{ nav.navigate("payment-detail/$it") },{ nav.navigate("export/ledger:${entry.id()}") }) } }
    composable("payment-select") { val state=bookState.value; SelectPaymentScreen(state,model,onAddTenant={ nav.navigate("flats") }) { nav.navigate("payment-new/$it") } }
    composable("payment-new/{id}") { entry -> val state=bookState.value; Loaded(state,model) { PaymentFormScreen(state,model,entry.id(),onDone={ nav.popBackStack() }) } }
    composable("payment-edit/{id}") { entry -> val state=bookState.value; Loaded(state,model) {
        val payment=state.book.payments.firstOrNull { it.id==entry.id() }
        if(payment==null) Text(AppText.get(R.string.payment_not_found)) else PaymentFormScreen(state,model,payment.tenancy_id,payment.id) { nav.popBackStack() }
    } }
    composable("payment-detail/{id}") { entry -> val state=bookState.value; Loaded(state,model) { PaymentDetailsScreen(state,model,entry.id(),
        { nav.navigate("payment-edit/$it") },{ nav.navigate("receipt/$it") },{ nav.navigate("files/payments/$it") }) } }
    composable("receipt/{id}") { entry -> val state=bookState.value; Loaded(state,model) { ReceiptScreen(state,model,entry.id()) } }
}
private fun NavGraphBuilder.ledgerRoutes(bookState:State<BookUiState>,model:BookViewModel,nav:NavHostController) {
    composable("expense-new") { val state=bookState.value; Loaded(state,model) { ExpenseFormScreen(state,model,onDone={ nav.popBackStack() }) } }
    composable("expense-edit/{id}") { entry -> val state=bookState.value; Loaded(state,model) { ExpenseFormScreen(state,model,entry.id()) { nav.popBackStack() } } }
    composable("maintenance") { val state=bookState.value; MaintenanceScreen(state,model,onEdit={ nav.navigate(if(it==null) "maintenance-new" else "maintenance-edit/$it") },onFiles={ nav.navigate(fileRoute(it)) }) }
    composable("maintenance/{id}") { entry -> val state=bookState.value; Loaded(state,model) { MaintenanceScreen(state,model,entry.id(),
        { nav.navigate(if(it==null) "maintenance-flat-new/${entry.id()}" else "maintenance-edit/$it") },{ nav.navigate(fileRoute(it)) }) } }
    composable("maintenance-new") { val state=bookState.value; Loaded(state,model) { MaintenanceFormScreen(state,model,onDone={ nav.popBackStack() }) } }
    composable("maintenance-flat-new/{id}") { entry -> val state=bookState.value; Loaded(state,model) { MaintenanceFormScreen(state,model,flatId=entry.id(),onDone={ nav.popBackStack() }) } }
    composable("maintenance-edit/{id}") { entry -> val state=bookState.value; Loaded(state,model) { MaintenanceFormScreen(state,model,entry.id(),onDone={ nav.popBackStack() }) } }
    composable("deposit/{id}") { entry -> val state=bookState.value; Loaded(state,model) { DepositScreen(state,model,entry.id()) } }
    composable("move-out/{id}") { entry -> val state=bookState.value; Loaded(state,model) { MoveOutScreen(state,model,entry.id()) { nav.popBackStack() } } }
    composable("recurring") { val state=bookState.value; RecurringScreen(state,model) }
}
private fun NavGraphBuilder.fileRoutes(bookState:State<BookUiState>,model:BookViewModel,nav:NavHostController,container:AppContainer) {
    composable("files/{entity}/{id}") { entry -> val state=bookState.value; Loaded(state,model) {
        val entity=entry.arguments?.getString("entity").orEmpty();val id=entry.id()
        val files:FilesViewModel=viewModel(key="files/$entity/$id",factory=viewModelFactory { initializer { FilesViewModel(container.filesRepository) } })
        FilesScreen(state,model,files,entity,id)
    } }
    composable("export/{format}") { entry -> val state=bookState.value; Loaded(state,model) { ExportScreen(state,model,entry.arguments?.getString("format") ?: "csv") } }
}
@Composable private fun Loaded(state:BookUiState,model:BookViewModel,content: @Composable () -> Unit) {
    if(state.loaded) content() else BookPage(state,model::refresh) {}
}
private fun NavBackStackEntry.id()=arguments?.getString("id").orEmpty()
private fun fileRoute(ref:String):String {
    val entity=ref.substringBefore('/');val id=ref.substringAfter('/')
    return "files/${if(entity=="expense") "expenses" else entity}/$id"
}
@Composable private fun BottomTabs(nav:NavHostController,route:String) {
    NavigationBar { Tab.entries.forEach { tab -> NavigationBarItem(selected=route==tab.route,
        onClick={ nav.navigate(tab.route) { popUpTo(nav.graph.findStartDestination().id) { saveState=true };launchSingleTop=true;restoreState=true } },
        icon={ Icon(painterResource(tab.icon),contentDescription=null) },label={ Text(appLabel(tab.title)) }) } }
}
@Composable private fun MoreScreen(onOpen:(String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SectionCard(AppText.get(R.string.manage_your_building)) {
            ActionRow(AppText.get(R.string.building_information), { onOpen("building") }, R.drawable.ic_flats)
            ActionRow(AppText.get(R.string.maintenance), { onOpen("maintenance") }, R.drawable.ic_maintenance)
            ActionRow(AppText.get(R.string.recurring_expenses), { onOpen("recurring") }, R.drawable.ic_repeat)
        }
        SectionCard(AppText.get(R.string.records)) {
            ActionRow(AppText.get(R.string.reports_and_export), { onOpen("reports") }, R.drawable.ic_report)
            ActionRow(AppText.get(R.string.search), { onOpen("search") }, R.drawable.ic_search)
            ActionRow(AppText.get(R.string.audit_history), { onOpen("audit") }, R.drawable.ic_document)
        }
        SectionCard(AppText.get(R.string.preferences)) {
            ActionRow(AppText.get(R.string.reminders), { onOpen("reminders") }, R.drawable.ic_calendar)
            ActionRow(AppText.get(R.string.settings), { onOpen("settings") }, R.drawable.ic_settings)
        }
        Text(AppText.get(R.string.prosanti_nibash_version_1_0),style=MaterialTheme.typography.bodySmall)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BookAppBar(route:String,state:BookUiState,model:BookViewModel,nav:NavHostController,onLock:()->Unit) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val isTab = Tab.entries.any { it.route == route }
    TopAppBar(title = { Text(routeTitle(route,state)) }, navigationIcon = {
        if (!isTab) IconButton(onClick = { dispatcher?.onBackPressed() }) { Icon(painterResource(R.drawable.ic_back), AppText.get(R.string.back)) }
    }, actions = {
        if (isTab && route != "more") {
            IconButton(onClick = { nav.navigate("search") }) { Icon(painterResource(R.drawable.ic_search), AppText.get(R.string.search_records)) }
            IconButton(onClick = model::refresh, enabled = !state.loading && !state.saving) { Icon(painterResource(R.drawable.ic_refresh), AppText.get(R.string.refresh_records)) }
        }
        if (route == "home") IconButton(onClick = onLock) { Icon(painterResource(R.drawable.ic_lock), AppText.get(R.string.lock_app)) }
    })
}
private fun routeTitle(route:String,state:BookUiState):String = when (route.substringBefore('/')) {
    "home" -> if (state.loaded) state.book.building.name else AppText.get(R.string.prosanti_nibash)
    "tenant-new" -> AppText.get(R.string.add_tenant)
    "tenant-edit" -> AppText.get(R.string.edit_tenant)
    "tenant" -> AppText.get(R.string.tenant_details)
    "flat" -> AppText.get(R.string.flat_details)
    "payment-select" -> AppText.get(R.string.record_payment)
    "payment-new" -> AppText.get(R.string.add_payment)
    "payment-edit" -> AppText.get(R.string.edit_payment)
    "payment-detail" -> AppText.get(R.string.payment_details)
    "expense-new" -> AppText.get(R.string.add_expense)
    "expense-edit" -> AppText.get(R.string.edit_expense)
    "maintenance-new", "maintenance-flat-new" -> AppText.get(R.string.add_maintenance)
    "maintenance-edit" -> AppText.get(R.string.edit_maintenance)
    "move-out" -> AppText.get(R.string.move_out)
    "files" -> AppText.get(R.string.documents)
    "recurring" -> AppText.get(R.string.recurring_expenses)
    "audit" -> AppText.get(R.string.audit_history)
    "building" -> AppText.get(R.string.building_information)
    else -> appLabel(route.substringBefore('/').replaceFirstChar { it.uppercase() })
}

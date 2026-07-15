package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WaterLog
import com.example.ui.HydrationEvent
import com.example.ui.WaterViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val viewModel: WaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HydroHabitApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydroHabitApp(viewModel: WaterViewModel) {
    val currentScreenIndex by viewModel.currentScreenIndex.collectAsStateWithLifecycle()
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val dailyTargetMl by viewModel.dailyTargetMl.collectAsStateWithLifecycle()
    val hasPromptedGoal by viewModel.hasPromptedGoal.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Dialog trigger states
    var showGoalDialog by remember { mutableStateOf(false) }
    var showCustomAddDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Listen to hydration events for goals/tracks
    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HydrationEvent.GoalReached -> {
                    snackbarHostState.showSnackbar(
                        message = "🎉 Goal Reached! You have achieved your target of ${event.target} ml!",
                        duration = SnackbarDuration.Long
                    )
                }
                is HydrationEvent.WaterTracked -> {
                    Toast.makeText(
                        context,
                        "Water Tracked 💧 Added ${event.amount} ml. Keep it up!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
    } else {
        // Startup Goal Dialog
        if (!hasPromptedGoal) {
            StartupGoalDialog(
                onDismiss = { target ->
                    viewModel.setDailyTarget(target)
                    viewModel.setHasPromptedGoal(true)
                    Toast.makeText(context, "Goal set to $target ml. Let's start tracking! 🎯", Toast.LENGTH_LONG).show()
                }
            )
        }

        // Goal Adjust Dialog
        if (showGoalDialog) {
            GoalCustomizerDialog(
                currentGoal = dailyTargetMl,
                onDismiss = { showGoalDialog = false },
                onConfirm = { newGoal ->
                    viewModel.setDailyTarget(newGoal)
                    showGoalDialog = false
                    Toast.makeText(context, "Goal updated to $newGoal ml. 🎯", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Custom Add Dialog
        if (showCustomAddDialog) {
            CustomAddDialog(
                onDismiss = { showCustomAddDialog = false },
                onConfirm = { amount ->
                    viewModel.addWater(amount)
                    showCustomAddDialog = false
                }
            )
        }

        // Reset Confirmation Dialog
        if (showResetDialog) {
            ResetConfirmDialog(
                onDismiss = { showResetDialog = false },
                onConfirm = {
                    viewModel.resetToday()
                    showResetDialog = false
                    Toast.makeText(context, "Today's logs cleared! 💧", Toast.LENGTH_SHORT).show()
                }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.background
                ) {
                    DrawerContent(
                        currentScreenIndex = currentScreenIndex,
                        onScreenSelected = { index ->
                            viewModel.setCurrentScreenIndex(index)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (currentScreenIndex == 0) "HydroHabit Tracker" else "Hydration History",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("menu_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Navigation Menu",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showGoalDialog = true },
                                modifier = Modifier.testTag("tune_goal_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Set Daily Goal",
                                    tint = Color.White
                                )
                            }
                            if (currentScreenIndex == 0) {
                                IconButton(
                                    onClick = { showResetDialog = true },
                                    modifier = Modifier.testTag("reset_logs_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset today's logs",
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (currentScreenIndex == 0) {
                        TrackerScreen(
                            logs = logs,
                            dailyTargetMl = dailyTargetMl,
                            onAddWater = { amount -> viewModel.addWater(amount) },
                            onCustomAddClick = { showCustomAddDialog = true },
                            viewModel = viewModel
                        )
                    } else {
                        HistoryScreen(
                            logs = logs,
                            onDeleteLog = { id -> viewModel.deleteLog(id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    currentScreenIndex: Int,
    onScreenSelected: (Int) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Drawer Header with gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 32.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "HydroHabit",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Stay hydrated, feel better.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Menu items
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.LocalDrink,
                    contentDescription = null,
                    tint = if (currentScreenIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    text = "Water Tracker",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            },
            selected = currentScreenIndex == 0,
            onClick = { onScreenSelected(0) },
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("drawer_tracker_item")
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (currentScreenIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    text = "Intake History",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            },
            selected = currentScreenIndex == 1,
            onClick = { onScreenSelected(1) },
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("drawer_history_item")
        )

        Spacer(modifier = Modifier.weight(1f))

        // Developer Info Footer Card
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "App Developed By:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ayush Acharya",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
                Text(
                    text = "Support & Feedback:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:contact@ayushacharya.info.np")
                                    putExtra(Intent.EXTRA_SUBJECT, "HydroHabit Support & Feedback")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .testTag("support_email_link")
                ) {
                    Icon(
                        imageVector = Icons.Default.MailOutline,
                        contentDescription = "EmailIcon",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "contact@ayushacharya.info.np",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun TrackerScreen(
    logs: List<WaterLog>,
    dailyTargetMl: Int,
    onAddWater: (Int) -> Unit,
    onCustomAddClick: () -> Unit,
    viewModel: WaterViewModel
) {
    val todayTotal = remember(logs) {
        logs.filter { viewModel.isToday(it.timestamp) }.sumOf { it.amountMl }
    }

    val progressPercentage = remember(todayTotal, dailyTargetMl) {
        if (dailyTargetMl <= 0) 0.0f
        else {
            val pct = todayTotal.toFloat() / dailyTargetMl.toFloat()
            if (pct > 1.0f) 1.0f else pct
        }
    }

    val pctAway = remember(progressPercentage) {
        ((1.0f - progressPercentage) * 100).toInt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Curved top header area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                // Circular Progress
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(180.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progressPercentage },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White,
                        strokeWidth = 12.dp,
                        trackColor = Color.White.copy(alpha = 0.24f),
                        strokeCap = StrokeCap.Round
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$todayTotal",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "/ $dailyTargetMl ml",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = if (todayTotal >= dailyTargetMl)
                        "Great job! You are perfectly hydrated!"
                    else
                        "Keep going! You are $pctAway% away from your target.",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Quick Intake Action Buttons
        Text(
            text = "Quick Add",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickAddButton(
                icon = Icons.Default.LocalDrink,
                label = "Small Glass",
                amount = 150,
                onAdd = onAddWater,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_add_150_button")
            )
            QuickAddButton(
                icon = Icons.Default.Coffee,
                label = "Medium Cup",
                amount = 250,
                onAdd = onAddWater,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_add_250_button")
            )
            QuickAddButton(
                icon = Icons.Default.WaterDrop,
                label = "Big Bottle",
                amount = 500,
                onAdd = onAddWater,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_add_500_button")
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Custom Intake Block
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Custom Intake",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add custom quantity in milliliters.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onCustomAddClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("custom_add_trigger_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Custom",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun QuickAddButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: Int,
    onAdd: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onAdd(amount) }
            .padding(vertical = 16.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$amount ml",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun HistoryScreen(
    logs: List<WaterLog>,
    onDeleteLog: (Int) -> Unit
) {
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No logs recorded yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log your drinks on the tracker to see them here.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
        val dateFullFormat = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                val timeString = remember(log.timestamp) {
                    val logCal = Calendar.getInstance().apply { timeInMillis = log.timestamp }
                    val todayCal = Calendar.getInstance()
                    if (logCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) &&
                        logCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                        "Today, ${dateFormat.format(Date(log.timestamp))}"
                    } else {
                        dateFullFormat.format(Date(log.timestamp))
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("history_log_item_${log.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalDrink,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${log.amountMl} ml",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Recorded at $timeString",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteLog(log.id) },
                            modifier = Modifier.testTag("delete_log_button_${log.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete Log Entry",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartupGoalDialog(
    onDismiss: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("2000") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {}, // User must set a goal to proceed!
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Welcome to HydroHabit!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Please enter your target daily water intake goal to get started.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        errorText = null
                    },
                    label = { Text("Daily Hydration Target") },
                    suffix = { Text("ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("startup_goal_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textValue.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        onDismiss(parsed)
                    } else {
                        errorText = "Please enter a valid positive number."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("startup_goal_submit")
            ) {
                Text("Let's Go!")
            }
        }
    )
}

@Composable
fun GoalCustomizerDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentGoal.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Adjust Daily Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        errorText = null
                    },
                    label = { Text("Daily Target") },
                    suffix = { Text("ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("goal_customizer_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textValue.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        onConfirm(parsed)
                    } else {
                        errorText = "Please enter a valid positive number."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("goal_customizer_save")
            ) {
                Text("Save")
            }
        }
    )
}

@Composable
fun CustomAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Custom Intake", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        errorText = null
                    },
                    placeholder = { Text("e.g., 350") },
                    suffix = { Text("ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_intake_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textValue.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        onConfirm(parsed)
                    } else {
                        errorText = "Please enter a valid positive number."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("custom_intake_submit")
            ) {
                Text("Add")
            }
        }
    )
}

@Composable
fun ResetConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Reset Logs?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "Are you sure you want to clear all your water intake logs for today?",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_reset_button")
            ) {
                Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var scale by remember { mutableStateOf(0.7f) }
    var alpha by remember { mutableStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(
            durationMillis = 1000,
            easing = { OvershootInterpolator(1.2f).getInterpolation(it) }
        ),
        label = "scale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(
            durationMillis = 1000,
            easing = { it } // Linear
        ),
        label = "alpha"
    )

    LaunchedEffect(key1 = true) {
        scale = 1.0f
        alpha = 1.0f
        kotlinx.coroutines.delay(2200)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0288D1),
                        Color(0xFF01579B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon),
                contentDescription = "HydroHabit Splash Logo",
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer(
                        scaleX = animatedScale,
                        scaleY = animatedScale,
                        alpha = animatedAlpha
                    )
                    .clip(RoundedCornerShape(36.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "HydroHabit",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.graphicsLayer(alpha = animatedAlpha)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Stay hydrated, feel better.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer(alpha = animatedAlpha)
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer(alpha = animatedAlpha)
            )
        }
    }
}


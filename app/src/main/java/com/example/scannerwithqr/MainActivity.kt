package com.example.scannerwithqr

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class Transaction(
    val date: String,
    val time: String,
    val originalAmount: Double,
    val discount: Double,
    val finalAmount: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") { HomeScreen(navController) }
                composable("transaction") { DiscountQRScreen(navController, context = this@MainActivity) }
                composable("history") { TransactionHistoryScreen(context = this@MainActivity) }
            }
        }
    }
}

// ---------------- HOME SCREEN ----------------
@Composable
fun HomeScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Transaction App", fontSize = 32.sp, modifier = Modifier.padding(bottom = 48.dp))
        Button(onClick = { navController.navigate("transaction") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Add Transaction")
        }
        Button(onClick = { navController.navigate("history") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Transaction History")
        }
    }
}

// ---------------- DISCOUNT + QR SCREEN ----------------
@Composable
fun DiscountQRScreen(navController: NavHostController, context: ComponentActivity) {
    var amountInput by remember { mutableStateOf(TextFieldValue("")) }
    var discountInput by remember { mutableStateOf(TextFieldValue("")) }
    var output by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQRDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Discount Calculator", fontSize = 28.sp, modifier = Modifier.padding(bottom = 24.dp))

        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it },
            label = { Text("Amount") },
            placeholder = { Text("Enter amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = discountInput,
            onValueChange = { discountInput = it },
            label = { Text("Discount %") },
            placeholder = { Text("Enter discount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val amount = amountInput.text.toDoubleOrNull()
                val discount = discountInput.text.toDoubleOrNull()
                output = if (amount != null && discount != null) {
                    String.format("%.2f", amount - (amount * discount / 100))
                } else "Invalid input"
                qrBitmap = null
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calculate") }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = output, onValueChange = {}, label = { Text("Final Amount") }, readOnly = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val finalAmount = output.toDoubleOrNull()
                if (finalAmount != null) {
                    val qrString = "upi://pay?pa=8527472442@ybl&am=$finalAmount"
                    qrBitmap = generateQRCode(qrString)
                    showQRDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate QR") }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val originalAmount = amountInput.text.toDoubleOrNull()
                val discount = discountInput.text.toDoubleOrNull()
                val finalAmount = output.toDoubleOrNull()
                if (originalAmount != null && discount != null && finalAmount != null) {
                    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val tx = Transaction(currentDate, currentTime, originalAmount, discount, finalAmount)
                    saveTransaction(context, tx)
                }
                navController.navigate("home") { popUpTo("home") { inclusive = true } }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Done") }

        if (showQRDialog && qrBitmap != null) {
            Dialog(onDismissRequest = { showQRDialog = false }, properties = DialogProperties(dismissOnClickOutside = false)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "UPI QR Code", modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f))
                        Button(onClick = { showQRDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

// ---------------- TRANSACTION HISTORY SCREEN ----------------
@Composable
fun TransactionHistoryScreen(context: ComponentActivity) {
    var allTransactions by remember { mutableStateOf(loadTransactions(context)) }
    var selectedDate by remember { mutableStateOf("") } // yyyy-MM-dd
    var whenFilter by remember { mutableStateOf(TextFieldValue("")) }
    val selectedTransactions = remember { mutableStateListOf<Transaction>() }

    // Date picker
    val calendar = Calendar.getInstance()
    val datePicker = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val filteredTransactions = allTransactions.filter { tx ->
        val mealType = getMealType(tx.time)
        (selectedDate.isEmpty() || tx.date == selectedDate) &&
                (whenFilter.text.isEmpty() || mealType.equals(whenFilter.text, true))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Transaction History", fontSize = 28.sp, modifier = Modifier.padding(bottom = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            // Date Filter Button
            Button(onClick = { datePicker.show() }, modifier = Modifier.weight(1f)) {
                Text(if (selectedDate.isEmpty()) "Select Date" else selectedDate)
            }

            // When Filter
            OutlinedTextField(
                value = whenFilter,
                onValueChange = { whenFilter = it },
                label = { Text("Filter by When") },
                placeholder = { Text("Breakfast/Lunch/Snacks/Dinner") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Table Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Select", modifier = Modifier.weight(0.5f))
            Text("Date", modifier = Modifier.weight(1f))
            Text("When", modifier = Modifier.weight(1f))
            Text("Original Amt", modifier = Modifier.weight(1f))
            Text("Discount", modifier = Modifier.weight(1f))
            Text("Final Amt", modifier = Modifier.weight(1f))
        }
        Divider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn {
            items(filteredTransactions) { tx ->
                val mealType = getMealType(tx.time)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedTransactions.contains(tx),
                        onCheckedChange = { checked ->
                            if (checked) selectedTransactions.add(tx) else selectedTransactions.remove(tx)
                        },
                        modifier = Modifier.weight(0.5f)
                    )
                    Text(tx.date, modifier = Modifier.weight(1f))
                    Text(mealType, modifier = Modifier.weight(1f))
                    Text("₹${String.format("%.2f", tx.originalAmount)}", modifier = Modifier.weight(1f))
                    Text("${tx.discount}%", modifier = Modifier.weight(1f))
                    Text("₹${String.format("%.2f", tx.finalAmount)}", modifier = Modifier.weight(1f))
                }
                Divider()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Delete selected button
        Button(
            onClick = {
                selectedTransactions.forEach { deleteTransaction(context, it) }
                allTransactions = loadTransactions(context)
                selectedTransactions.clear()
            },
            enabled = selectedTransactions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Delete Selected") }
    }
}

// ---------------- HELPERS ----------------
fun getMealType(time: String): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val date = sdf.parse(time) ?: return "Other"
    val cal = Calendar.getInstance()
    cal.time = date
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)

    return when {
        hour in 7..10 -> "Breakfast"
        hour in 12..14 -> "Lunch"
        hour in 16..18 && !(hour == 18 && minute > 30) -> "Snacks"
        hour in 19..21 && !(hour == 21 && minute > 30) -> "Dinner"
        else -> "Other"
    }
}

fun generateQRCode(text: String): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) for (y in 0 until height)
        bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp
}

fun saveTransaction(context: ComponentActivity, transaction: Transaction, fileName: String = "transactions.json") {
    val file = File(context.filesDir, fileName)
    val gson = Gson()
    val transactions: MutableList<Transaction> = if (file.exists()) {
        val type = object : TypeToken<MutableList<Transaction>>() {}.type
        gson.fromJson(file.readText(), type)
    } else mutableListOf()
    transactions.add(transaction)
    file.writeText(gson.toJson(transactions))
}

fun loadTransactions(context: ComponentActivity, fileName: String = "transactions.json"): List<Transaction> {
    val file = File(context.filesDir, fileName)
    if (!file.exists()) return emptyList()
    val gson = Gson()
    val type = object : TypeToken<List<Transaction>>() {}.type
    return gson.fromJson(file.readText(), type)
}

fun deleteTransaction(context: ComponentActivity, transaction: Transaction, fileName: String = "transactions.json") {
    val file = File(context.filesDir, fileName)
    if (!file.exists()) return
    val gson = Gson()
    val type = object : TypeToken<MutableList<Transaction>>() {}.type
    val transactions: MutableList<Transaction> = gson.fromJson(file.readText(), type)
    transactions.removeAll { it.date == transaction.date && it.time == transaction.time }
    file.writeText(gson.toJson(transactions))
}

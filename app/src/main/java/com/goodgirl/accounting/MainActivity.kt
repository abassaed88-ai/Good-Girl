package com.goodgirl.accounting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandBlue = Color(0xFF2878C8)
private val LightGray = Color(0xFFE9EEF4)
private val SoftGreen = Color(0xFFA9DDBD)
private val SoftRed = Color(0xFFE9A5AE)
private val SoftPurple = Color(0xFFB9A7DF)
private val Ink = Color(0xFF263445)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(40, 120, 200)
        window.navigationBarColor = android.graphics.Color.rgb(255, 255, 255)
        setContent {
            CompositionLocalProviderRtl {
                GoodGirlApp()
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content
    )
}

@Composable
private fun GoodGirlApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandBlue,
            secondary = SoftPurple,
            background = LightGray,
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Ink
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LightGray
        ) {
            DashboardScreen()
        }
    }
}

@Composable
private fun DashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandBlue, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Good Girl",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "نظام إدارة ومحاسبة متكامل",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "النسخة التأسيسية — البيانات المعروضة تجريبية",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp
            )
        }

        Text(
            text = "لوحة التحكم",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("مبيعات اليوم", "0", "ل.س", BrandBlue, Modifier.weight(1f))
            SummaryCard("مقبوضات اليوم", "0", "ل.س", SoftGreen, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("ديون مستحقة", "0", "ل.س", SoftRed, Modifier.weight(1f))
            SummaryCard("عدد الفواتير", "0", "فاتورة", SoftPurple, Modifier.weight(1f))
        }

        Text(
            text = "الأقسام الرئيسية",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        val sections = listOf(
            "المبيعات والفواتير",
            "المشتريات",
            "المنتجات والمخزون",
            "العملاء والموردون",
            "الحسابات والديون",
            "الصندوق والمصروفات",
            "التقارير والأرباح",
            "العملات وأسعار الصرف"
        )
        sections.forEachIndexed { index, title ->
            SectionRow(number = index + 1, title = title)
        }

        Text(
            text = "يتم بناء الوظائف وربطها بقاعدة بيانات محلية في المراحل التالية.",
            color = Color(0xFF64748B),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, color = Color(0xFF64748B), fontSize = 12.sp)
            Text(value, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionRow(number: Int, title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = number.toString(),
                color = BrandBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

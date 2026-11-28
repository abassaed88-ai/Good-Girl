package com.goodgirl.accounting

import androidx.compose.ui.test.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardNavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardMasterDataTileOpensMasterDataScreen() {
        composeRule.onNodeWithTag("dashboard-master-data")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("إدارة البيانات الأساسية").assertIsDisplayed()
        composeRule.onNodeWithText("تصنيفات المنتجات").assertIsDisplayed()
        composeRule.onNodeWithText("العملاء والموردون").assertIsDisplayed()
    }

    @Test
    fun dashboardMenuOpensAndMasterDataItemNavigates() {
        composeRule.onNodeWithContentDescription("القائمة").performClick()
        composeRule.waitForIdle()
        // Select the clickable menu item, avoiding the duplicated dashboard heading.
        composeRule.onNode(hasText("إدارة البيانات الأساسية") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("إدارة البيانات الأساسية").assertIsDisplayed()
        composeRule.onNodeWithText("المنتجات والمخزون").assertIsDisplayed()
    }
}

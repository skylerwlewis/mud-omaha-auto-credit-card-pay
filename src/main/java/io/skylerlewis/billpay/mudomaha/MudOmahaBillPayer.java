package io.skylerlewis.billpay.mudomaha;

import io.skylerlewis.billpay.mudomaha.authenticator.MudOmahaAuthenticator;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MudOmahaBillPayer {

    private static Logger LOGGER = LoggerFactory.getLogger(MudOmahaBillPayer.class);

    private static GmailMessageSender messageSender;

    public static void main(String[] args) {
        if (args.length < 5) {
            LOGGER.error("Must pass in parameters like so: <mudOmahaUsername> <mudOmahaPassword> <gmailUsername> <gmailPassword> <errorEmail>");
            System.exit(1);
        }

        String username = args[0];
        String password = args[1];
        String gmailUsername = args[2];
        String gmailPassword = args[3];
        String toAddress = args[4];

        // Optional. If not specified, WebDriver searches the PATH for chromedriver.
        if (args.length > 5) {
            System.setProperty("webdriver.chrome.driver", args[5]);
        }

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--headless");
        WebDriver driver = new ChromeDriver(chromeOptions);

        try {
            messageSender = new GmailMessageSender(gmailUsername, gmailPassword, toAddress);

            WebDriverWait wait = new WebDriverWait(driver, 120);

            MudOmahaAuthenticator.login(username, password, driver);

            wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#__xmlview1--payMyBillTile-number.sapMStdTileNumS")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#__xmlview1--payMyBillTile-number.sapMStdTileNumM"))));

            List<String> screenshotData = new ArrayList<>();
            if (driver instanceof TakesScreenshot) {
                screenshotData.add(((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64));
            }

            WebElement amountDueDiv = driver.findElement(By.cssSelector("#__xmlview1--payMyBillTile-number"));
            String amountDueText = amountDueDiv.getText();
            BigDecimal amountDue = new BigDecimal(amountDueText.replaceFirst("\\$", "").trim());

            if (BigDecimal.ZERO.compareTo(amountDue) < 0) {
                String amountDueString = "$" + amountDue.setScale(2).toPlainString();
                LOGGER.info("MUD Omaha bill due: " + amountDueString);
                boolean success = payBill(driver, amountDue, screenshotData);
                String[] screenshots = screenshotData.toArray(new String[0]);
                String message;
                if (success) {
                    message = String.format("MUD Omaha bill (%s) was paid successfully.", amountDueString);
                    LOGGER.info(message);
                } else {
                    message = "The amount in the pay button didn't match the expected amount";
                    LOGGER.error(message);
                }
                messageSender.sendMessage(message, screenshots);
            } else {
                LOGGER.info("No MUD Omaha bill was due.");
            }

            MudOmahaAuthenticator.logout(driver);
        } catch (Exception e) {
            String message = "There was a problem paying the MUD Omaha bill automatically.";
            LOGGER.error(message, e);
            messageSender.sendMessage(message);
        }

        driver.quit();
    }

    private static boolean payBill(WebDriver driver, BigDecimal amountDue, List<String> screenshotData) {
        boolean success = false;
        WebDriverWait wait = new WebDriverWait(driver, 120);

        WebElement payMyBillButton = driver.findElement(By.id("__xmlview1--payMyBillTile"));

        String mainWindow = driver.getWindowHandle();

        payMyBillButton.click();

        Set windowHandles = driver.getWindowHandles();

        Iterator windowHandlesIterator = windowHandles.iterator();

        while (windowHandlesIterator.hasNext()) {
            String popupHandle = windowHandlesIterator.next().toString();
            if (!popupHandle.contains(mainWindow)) {
                driver.switchTo().window(popupHandle);

                if (driver instanceof TakesScreenshot) {
                    screenshotData.add(((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64));
                }

                WebElement payBillButton = driver.findElement(By.cssSelector("a[id^=account-expressPay]"));
                payBillButton.click();
                wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[title=Continue]")));

                if (driver instanceof TakesScreenshot) {
                    screenshotData.add(((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64));
                }

                WebElement continueButton = driver.findElement(By.cssSelector("a[title=Continue]"));
                continueButton.click();
                wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[data-id=btn-payment-confirmation]")));

                if (driver instanceof TakesScreenshot) {
                    screenshotData.add(((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64));
                }

                WebElement payButton = driver.findElement(By.cssSelector("a[data-id=btn-payment-confirmation]"));
                String payButtonText = payButton.getText();
                BigDecimal payButtonAmount = new BigDecimal(payButtonText.replaceAll("Pay", "").replaceFirst("\\$", "").trim());
                if (amountDue.compareTo(payButtonAmount) == 0) {
                    payButton.click();
                    LOGGER.info("Clicked the pay button");

                    wait.until(ExpectedConditions.elementToBeClickable(By.id("logoutCloseWindowBtn")));

                    if (driver instanceof TakesScreenshot) {
                        screenshotData.add(((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64));
                    }

                    success = true;
                }

                driver.close();

                //After finished your operation in pop-up just select the main window again
                driver.switchTo().window(mainWindow);
            }
        }
        return success;
    }

}

package com.kalypso.fileloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Automatic tool to upload files to agile using the import tool. Requirements, The agile variable be set to The agile url you want to access Set the
 * username and password to valid ones to that agile server. Mappings file for all subclasses must be all in Mappings_path Download_path is to where
 * the logs are saved when you download the logs from agile The Logfile_default_name is the name the logs are being saved as when you click to
 * download the logs from agile remember to check that there is not a file already named like that. The xlsx files must be named with an specific
 * syntax to actually parse them. XXX_<ecoNumber>_<class_name><consecutive number if any>.xlsx where the first XXX are 3 characters that you want
 * Mappings file name should be the same as the subclass you are uploading
 * 
 * IF at any time agile changes it's id's or whatever, this will break.
 * 
 * @author Karlo Mendoza
 *
 */
public class Selenium {

	private static final String Agile = "http://icuaglapp201.icumed.com:7023/Agile/PCMServlet";

	private static final Boolean uploadEvenWithErrors = true;

	private static final String METADATA_PATH = "C:\\Users\\Karlo Mendoza\\Box Sync\\Clients\\ICU Medical\\ICU Medical PLM Implementation\\Workstreams\\Program Data Migration\\Data Files\\SAP-DMS\\Training\\upload\\";
	private static final String MAPPINGS_PATH = "C:\\Users\\Karlo Mendoza\\Box Sync\\Clients\\ICU Medical\\ICU Medical PLM Implementation\\Workstreams\\Program Data Migration\\Data Files\\SAP-DMS\\Training\\mapping\\";
	private static final String DOWNLOAD_PATH = "C:\\Users\\Karlo Mendoza\\Downloads\\";
	private static final String LOGFILE_DEFAULT_NAME = "LogFile.xml";

	private static final String USERNAME = "data.loader";
	private static final String PASSWORD = "agile";

	private static String loginHandler = "";
	private static String mainWindowHandler = "";

	private static boolean useEcos = true;

	public static void main(String[] args) throws IOException, InterruptedException {

		System.setProperty("webdriver.chrome.driver", "C:\\Users\\Karlo Mendoza\\Downloads\\chromedriver_win32\\chromedriver.exe");

		Map<String, Object> chromePrefs = new HashMap<String, Object>();
		chromePrefs.put("profile.default_content_settings.popups", 0);
		chromePrefs.put("safebrowsing.enabled", "true");
		ChromeOptions options = new ChromeOptions();
		options.setExperimentalOptions("prefs", chromePrefs);
		DesiredCapabilities cap = DesiredCapabilities.chrome();
		cap.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
		cap.setCapability(ChromeOptions.CAPABILITY, options);
		WebDriver driver = new ChromeDriver(cap);

		logIn(driver);
		goToImportPage(driver);

		// Until here we are already logged in and in the import tool window

		File filesToUpload = new File(METADATA_PATH);

		for (File file : filesToUpload.listFiles()) {
			if (file.isDirectory() || !file.getName().endsWith("xlsx"))
				continue;

			ParsedFile parsedFile = parseFile(file);
			try {
				WebDriverWait wait2 = new WebDriverWait(driver, 30);
				wait2.until(ExpectedConditions.elementToBeClickable(By.id("importSourceFile")));

				WebElement fileInput = driver.findElement(By.id("importSourceFile"));
				fileInput.sendKeys(file.getAbsolutePath());

				WebDriverWait wait21 = new WebDriverWait(driver, 60);
				wait21.until(ExpectedConditions.elementToBeClickable(By.id("config_options")));

				driver.findElement(By.id("cmdNextspan")).click();

				WebDriverWait wait31 = new WebDriverWait(driver, 20);
				wait31.until(ExpectedConditions.elementToBeClickable(By.id("cmdNextspan")));

				driver.findElement(By.id("cmdNextspan")).click();

				WebDriverWait wait3 = new WebDriverWait(driver, 20);
				wait3.until(ExpectedConditions.elementToBeClickable(By.name("mapSourceType")));

				if (useEcos) {
					driver.findElements(By.name("redlineOption")).get(1).click();
					WebElement eco_number = driver.findElement(By.id("search_query_cgName_display"));
					eco_number.sendKeys(parsedFile.eco);
				} else {
					// Select no ECO required option
					driver.findElements(By.name("redlineOption")).get(0).click();
					;
				}

				List<WebElement> mappingRadios = driver.findElements(By.name("mapSourceType"));
				mappingRadios.get(1).click();

				WebElement mapping = driver.findElement(By.id("mappingSourceFile"));
				mapping.sendKeys(MAPPINGS_PATH + parsedFile.nameWithoutExtension + ".xml");
				mappingRadios.get(1).click();

				Thread.sleep(3000);
				try {
					driver.findElement(By.id("cmdNextspan")).click();
				} catch (Exception ex) {
					Thread.sleep(6000);
					driver.findElement(By.id("cmdNextspan")).click();
				}

				WebDriverWait wait4 = new WebDriverWait(driver, 20);
				wait4.until(ExpectedConditions.elementToBeClickable(By.id("cmdValidatespan")));

				driver.findElement(By.id("cmdValidatespan")).click();

				WebDriverWait wait5 = new WebDriverWait(driver, 30);
				wait5.until(ExpectedConditions.elementToBeClickable(By.id("cmdSaveLogspan")));

				String appendWhenError = "";
				// wait until it finishes the validation, or we get and error
				for (int tries = 0; tries <= 60; tries++) {
					Thread.sleep(30000);
					String errorsAsText = "1";
					List<WebElement> findElements = driver.findElements(By.tagName("dt"));
					for (int i = 0; i < findElements.size(); i++) {
						if (findElements.get(i).getText().contains("Errors")) {
							List<WebElement> dds = driver.findElements(By.tagName("dd"));
							errorsAsText = dds.get(i).getText();
							errorsAsText = errorsAsText.replaceAll("<span>", "").replaceAll("</span>", "");
							break;
						}
					}

					if (uploadEvenWithErrors) {
						errorsAsText = "0";
					}

					if (Integer.valueOf(errorsAsText) == 0) {
						if (driver.findElement(By.id("cmdImport")).getAttribute("class").contains("disabled")) {
							System.out.println("Attemp #" + tries + " for file: " + parsedFile.nameWithoutExtension + parsedFile.fileNumber);
							continue;
						} else {
							driver.findElement(By.id("cmdImport")).click();
							break;
						}
						// if it has errors, lets wait 1 more minute to try and catch all the errors;
					} else {
						System.out.println("Found error for file: " + parsedFile.nameWithoutExtension + parsedFile.fileNumber + " sleeping 1 min");
						Thread.sleep(60000);
						appendWhenError = "error";
						break;
					}
				}
				// wait until we can download the logFile, either if its after an import of
				// because we got some errors in the validation
				for (int tries = 0; tries <= 60; tries++) {
					if (driver.findElement(By.id("cmdSaveLog")).getAttribute("class").contains("disabled")) {
						Thread.sleep(30000);
						continue;
					} else {
						break;
					}
				}

				driver.findElement(By.id("cmdSaveLog")).click();

				Thread.sleep(10000);
				File log = new File(DOWNLOAD_PATH + LOGFILE_DEFAULT_NAME);
				Files.move(Paths.get(log.getAbsolutePath()),
						Paths.get(log.getParentFile() + "\\" + appendWhenError + parsedFile.fullNameWithoutExtension + ".xml"));

				driver.findElement(By.id("cmdImportAnotherFilespan")).click();
			} catch (Exception ex) {
				System.out.println(parsedFile.nameWithoutExtension);
				ex.printStackTrace();

				driver.close();
				driver = new ChromeDriver(cap);
				logIn(driver);
				goToImportPage(driver);
			}
		}
		driver.close();
	}

	private static void closeAllOpenWindows(WebDriver driver) {
		Set<String> handles = driver.getWindowHandles(); // get all window handles

		for (String handler : handles) {
			driver.switchTo().window(handler);
			driver.close();
		}

	}

	private static ParsedFile parseFile(File file) {
		String nameWithNoise = file.getName();
		String eco = nameWithNoise.split("_")[1];

		if (!useEcos) {
			eco = "";
		}

		String numberWithNoise = nameWithNoise.substring(nameWithNoise.length() - 8, nameWithNoise.length() - 5);
		String number = "";
		for (char elem : numberWithNoise.toCharArray()) {
			if (elem == '0' || elem == '1' || elem == '2' || elem == '3' || elem == '4' || elem == '5' || elem == '6' || elem == '7' || elem == '8'
					|| elem == '9') {
				number += elem;
			}
		}

		String fullNameWithoutExtension = nameWithNoise.substring(0, nameWithNoise.length() - 5);
		String nameWithoutExtension = nameWithNoise.substring(12, nameWithNoise.length() - 5 - number.length());
		if (!useEcos) {
			nameWithoutExtension = nameWithNoise.substring(3, nameWithNoise.length() - 5 - number.length());
		}
		return new ParsedFile(eco, nameWithoutExtension, number, fullNameWithoutExtension);
	}

	static class ParsedFile {
		public String eco;
		public String nameWithoutExtension;
		public String fileNumber;
		public String fullNameWithoutExtension;

		ParsedFile(String eco, String nameWithoutExtension, String fileNumber, String fullNameWithoutExtension) {
			this.eco = eco;
			this.nameWithoutExtension = nameWithoutExtension;
			this.fileNumber = fileNumber;
			this.fullNameWithoutExtension = fullNameWithoutExtension;
		}
	}

	public static void logIn(WebDriver driver) {
		driver.get(Agile);

		driver.findElement(By.id("j_username")).sendKeys(USERNAME);
		driver.findElement(By.id("j_password")).sendKeys(PASSWORD);

		driver.findElement(By.id("login")).click();

		System.out.println(driver.getTitle());

		loginHandler = driver.getWindowHandle();
		String subWindowHandler = "";
		Set<String> handles = driver.getWindowHandles(); // get all window handles

		for (String handler : handles) {
			if (handler.equals(loginHandler))
				continue;
			subWindowHandler = handler;
		}
		driver.switchTo().window(subWindowHandler); // switch to popup window

		mainWindowHandler = subWindowHandler;

	}

	public static void goToImportPage(WebDriver driver) {

		WebDriverWait wait = new WebDriverWait(driver, 30);
		wait.until(ExpectedConditions.elementToBeClickable(By.id("preferences")));

		driver.findElement(By.id("preferences")).click();
		try {
			driver.findElement(By.id("yui-gen0")).click();
		} catch (Exception ex) {
			driver.findElement(By.id("yui-gen1")).click();
		}

		Set<String> handler2 = driver.getWindowHandles(); // get all window handles
		String subWindowHandler = "";
		for (String handler : handler2) {
			if (handler.equals(mainWindowHandler) || handler.equals(loginHandler))
				continue;
			subWindowHandler = handler;
		}
		driver.switchTo().window(subWindowHandler); // switch to popup window

	}

}

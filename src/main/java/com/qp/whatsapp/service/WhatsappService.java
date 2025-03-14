package com.qp.whatsapp.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.qp.whatsapp.dao.Whatsappdao;
import com.qp.whatsapp.dto.WhatsappNumbers;

@Service
public class WhatsappService {

	@Autowired
	Whatsappdao whatsappdao;

	@Autowired
	FileUploader fileUploader;

	public void addNumbers(WhatsappNumbers numbers) {
		whatsappdao.saveWhatsappNumber(numbers);
	}

	public List<WhatsappNumbers> fetchAllWhatsapp() {
		List<WhatsappNumbers> list = whatsappdao.fetchAllWhatsapp();
		return list.isEmpty() ? null : list;
	}

	private String createMessageBody(String phoneNumber, String templateName, String language) {
		return "{\n" + "  \"messaging_product\": \"whatsapp\",\n" + "  \"to\": \"" + phoneNumber + "\",\n"
				+ "  \"type\": \"template\",\n" + "  \"template\": {\n" + "    \"name\": \"" + templateName + "\",\n"
				+ "    \"language\": { \"code\": \"" + language + "\" }\n" + "  }\n" + "}";
	}

	public ResponseEntity<Object> sendMessage(MultipartFile file, String whatsappId, String templateName,
			String language) {
		File analyticsDirectory = new File("analytics");
		if (!analyticsDirectory.exists()) {
			analyticsDirectory.mkdirs();
		}

		File analyticsFile = new File(analyticsDirectory, "analytics.xlsx");

		try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(analyticsFile)) {
			Sheet sheet = workbook.createSheet("Analytics");
			Row headerRow = sheet.createRow(0);
			headerRow.createCell(0).setCellValue("Phone Number");
			headerRow.createCell(1).setCellValue("Status");
			headerRow.createCell(2).setCellValue("Message");

			File tempFile = File.createTempFile("uploaded-", file.getOriginalFilename());
			file.transferTo(tempFile);

			WhatsappNumbers whatsapp = whatsappdao.find(whatsappId);

			if (file.getOriginalFilename().endsWith(".xlsx") || file.getOriginalFilename().endsWith(".xls")) {
				return handleExcelFile(tempFile, whatsapp, templateName, language, sheet, analyticsFile);
			} else if (file.getOriginalFilename().endsWith(".csv")) {
				return handleCSVFile(tempFile, whatsapp, templateName, language, sheet, analyticsFile);
			} else {
				return ResponseEntity.badRequest().body("Unsupported file type. Please upload an Excel or CSV file.");
			}
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("Error processing the file: " + e.getMessage());
		}
	}

	private ResponseEntity<Object> handleExcelFile(File file, WhatsappNumbers whatsapp, String templateName,
			String language, Sheet sheet, File analyticsFile) {
		try (Workbook workbook = new XSSFWorkbook(file)) {
			Sheet excelSheet = workbook.getSheetAt(0);
			Iterator<Row> rows = excelSheet.iterator();

			int phoneColumnIndex = findColumnIndex(rows, "phone numbers");
			if (phoneColumnIndex == -1) {
				return ResponseEntity.badRequest().body("Column 'phone numbers' not found.");
			}

			int rowIndex = 1;
			while (rows.hasNext()) {
				Row row = rows.next();
				Cell cell = row.getCell(phoneColumnIndex);
				if (cell != null) {
					String phoneNumber = getCellValueAsString(cell);
					rowIndex = sendWhatsAppMessage(whatsapp, templateName, language, phoneNumber, sheet, rowIndex);
				}
			}

			try (FileOutputStream fos = new FileOutputStream(analyticsFile)) {
				sheet.getWorkbook().write(fos);
			}
			String fileUrl = fileUploader.handleFileUpload(analyticsFile);
			Map<String, Object> response = new HashMap<>();
			response.put("message", "Messages sent successfully");
			response.put("code", HttpStatus.OK.value());
			response.put("status", "success");
			response.put("data", fileUrl);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put("message", "Internal Server Error");
			response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
			response.put("status", "fail");
			return ResponseEntity.internalServerError().body(response);
		}
	}

	private ResponseEntity<Object> handleCSVFile(File file, WhatsappNumbers whatsapp, String templateName,
			String language, Sheet sheet, File analyticsFile) {
		Map<String, Object> response = new HashMap<String, Object>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			int phoneColumnIndex = findColumnIndex(reader.readLine(), "phone numbers");
			if (phoneColumnIndex == -1) {
				return ResponseEntity.badRequest().body("Column 'phone numbers' not found.");
			}

			int rowIndex = 1;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split(",");
				String phoneNumber = values[phoneColumnIndex];
				rowIndex = sendWhatsAppMessage(whatsapp, templateName, language, phoneNumber, sheet, rowIndex);
			}

			try (FileOutputStream fos = new FileOutputStream(analyticsFile)) {
				sheet.getWorkbook().write(fos);
			}

			String fileUrl = fileUploader.handleFileUpload(analyticsFile);
			response.put("message", "Messages sent successfully");
			response.put("code", HttpStatus.OK.value());
			response.put("status", "success");
			response.put("data", fileUrl);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			e.printStackTrace();
			response.put("message", "Internal Server Error");
			response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
			response.put("status", "fail");
			return ResponseEntity.internalServerError().body(response);
		}
	}

	private int sendWhatsAppMessage(WhatsappNumbers whatsapp, String templateName, String language, String phoneNumber,
			Sheet sheet, int rowIndex) {
		RestTemplate restTemplate = new RestTemplate();
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(whatsapp.getAccesstoken());

			String requestBody = createMessageBody(phoneNumber, templateName, language);
			HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

			ResponseEntity<String> response = restTemplate.exchange(
					"https://graph.facebook.com/v21.0/" + whatsapp.getPhoneNumberId() + "/messages", HttpMethod.POST,
					requestEntity, String.class);

			Row row = sheet.createRow(rowIndex++);
			row.createCell(0).setCellValue(phoneNumber);

			if (response.getStatusCode() == HttpStatus.OK) {
				row.createCell(1).setCellValue("Success");
				row.createCell(2).setCellValue("");
			} else {
				row.createCell(1).setCellValue("Failure");
				row.createCell(2).setCellValue(response.getBody());
			}
		} catch (Exception e) {
			String mesg = e.getMessage().contains("no body") ? "expired access token" : "Error";
			Row row = sheet.createRow(rowIndex++);
			row.createCell(0).setCellValue(phoneNumber);
			row.createCell(1).setCellValue("Failure");
			row.createCell(2).setCellValue(mesg);
		}
		return rowIndex;
	}

	private int findColumnIndex(Iterator<Row> rows, String columnName) {
		if (rows.hasNext()) {
			Row headerRow = rows.next();
			for (Cell cell : headerRow) {
				if (cell.getStringCellValue().equalsIgnoreCase(columnName)) {
					return cell.getColumnIndex();
				}
			}
		}
		return -1;
	}

	private int findColumnIndex(String headerLine, String columnName) {
		String[] headers = headerLine.split(",");
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].equalsIgnoreCase(columnName)) {
				return i;
			}
		}
		return -1;
	}

	private String getCellValueAsString(Cell cell) {
		switch (cell.getCellType()) {
		case STRING:
			return cell.getStringCellValue();
		case NUMERIC:
			return DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue().toString()
					: String.valueOf((long) cell.getNumericCellValue());
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		default:
			return "";
		}
	}

	public ResponseEntity<Object> sendMessagewithArray(String[] array, String whatsappId, String templateName,
			String language) {
		Map<String, Object> responsedata = new HashMap<String, Object>();
		try {
			if (array == null || array.length == 0) {
				return ResponseEntity.badRequest().body("Please enter phone numbers.");
			}

			List<String[]> analyticsList = new ArrayList<>();
			WhatsappNumbers whatsapp = whatsappdao.find(whatsappId);
			for (String phoneNumber : array) {
				System.out.println(phoneNumber);
				RestTemplate restTemplate = new RestTemplate();
				String accessToken = whatsapp.getAccesstoken();
				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.APPLICATION_JSON);
				headers.setBearerAuth(accessToken);
				String requestBody = createMessageBody(phoneNumber, templateName, language);
				HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
				System.out.println("before");
				ResponseEntity<String> response = restTemplate.exchange(
						"https://graph.facebook.com/v21.0/" + whatsapp.getPhoneNumberId() + "/messages",
						HttpMethod.POST, request, String.class);
				System.out.println("after");
				String status = (response.getStatusCode() == HttpStatus.OK) ? "Success" : "Failure";
				String message = (response.getStatusCode() == HttpStatus.OK) ? "" : response.getBody();
				analyticsList.add(new String[] { phoneNumber, status, message });
			}

			File analyticsDirectory = new File("analytics");
			if (!analyticsDirectory.exists()) {
				analyticsDirectory.mkdirs();
			}
			File analyticsFile = new File(analyticsDirectory, "analytics.xlsx");

			try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(analyticsFile)) {
				Sheet sheet = workbook.createSheet("Analytics");
				Row headerRow = sheet.createRow(0);
				headerRow.createCell(0).setCellValue("Phone Number");
				headerRow.createCell(1).setCellValue("Status");
				headerRow.createCell(2).setCellValue("Message");
				int rowIndex = 1;
				for (String[] analyticsData : analyticsList) {
					Row row = sheet.createRow(rowIndex++);
					row.createCell(0).setCellValue(analyticsData[0]);
					row.createCell(1).setCellValue(analyticsData[1]);
					row.createCell(2).setCellValue(analyticsData[2]);
				}
				workbook.write(fos);
			} catch (IOException e) {
				return ResponseEntity.internalServerError()
						.body("Error generating analytics Excel file: " + e.getMessage());
			}
			String fileUrl = fileUploader.handleFileUpload(analyticsFile);

			responsedata.put("message", "Messages sent successfully");
			responsedata.put("code", HttpStatus.OK.value());
			responsedata.put("status", "success");
			responsedata.put("data", fileUrl);
			return ResponseEntity.ok(responsedata);
		} catch (Exception e) {
			responsedata.put("message", e.getMessage());
			responsedata.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
			responsedata.put("status", "fail");
			return ResponseEntity.internalServerError().body(responsedata);
		}
	}

	public ResponseEntity<Map<String, Object>> getTemplates(String whatsappId) {
		Map<String, Object> map = new HashMap<>();
		try {
			WhatsappNumbers whatsapp = whatsappdao.find(whatsappId);

			RestTemplate restTemplate = new RestTemplate();
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(whatsapp.getAccesstoken());

			String apiurl = "https://graph.facebook.com/v21.0/" + whatsapp.getWhatsappId()
					+ "/message_templates?fields=name,status,language";
			HttpEntity<String> requestEntity = new HttpEntity<>(headers);

			ResponseEntity<JsonNode> response = restTemplate.exchange(apiurl, HttpMethod.GET, requestEntity,
					JsonNode.class);

			map.put("code", HttpStatus.OK.value());
			map.put("status", "success");
			map.put("data", response.getBody());
			return ResponseEntity.ok(map);
		} catch (Exception e) {
			map.put("message", e.getMessage());
			map.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
			map.put("status", "fail");
			return ResponseEntity.internalServerError().body(map);
		}
	}
}

package com.qp.whatsapp.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.qp.whatsapp.dto.WhatsappNumbers;
import com.qp.whatsapp.service.WhatsappService;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {

	@Autowired
	WhatsappService whatsappService;

//	@PostMapping("/sendBulk")
//	public String sendBulkMessages(@RequestBody List<String> phoneNumbers) {
//		whatsappService.sendBulkMessages(phoneNumbers);
//		return "Messages sent successfully!";
//	}

	@GetMapping("/fetch/all/whatsapp-numbers")
	public List<WhatsappNumbers> fetchAllWhatsapp() {
		return whatsappService.fetchAllWhatsapp();
	}

	@PostMapping("/send/bulk/whatsapp-message")
	public ResponseEntity<Object> sendBulkMessage(@RequestBody(required = false) MultipartFile file,
			@RequestParam String whatsappId, @RequestParam(required = false) String[] array,
			@RequestParam String templateName, @RequestParam String language) {
		if (whatsappId == null || whatsappId.trim().isEmpty()) {
			return new ResponseEntity<Object>("please select the whatsapp number", HttpStatus.BAD_REQUEST);
		}
		boolean isFileProvided = file != null && !file.isEmpty();
		boolean isArrayProvided = array != null && array.length > 0;
		if (isFileProvided) {
			return whatsappService.sendMessage(file, whatsappId, templateName, language);
		}
		if (isArrayProvided) {
			return whatsappService.sendMessagewithArray(array, whatsappId, templateName, language);
		}
		return new ResponseEntity<>("Please provide a file or WhatsApp numbers", HttpStatus.BAD_REQUEST);

	}
}

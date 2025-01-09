package com.qp.whatsapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qp.whatsapp.dto.WhatsappNumbers;
import com.qp.whatsapp.service.WhatsappService;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappAdminController {
	
	@Autowired
	WhatsappService service;

	@PostMapping("/add/whatspp-numbers")
	public ResponseEntity<Object> addNumbers(@RequestBody WhatsappNumbers numbers) {
		service.addNumbers(numbers);
		return new ResponseEntity<Object>("Whatsapp number added successfully", HttpStatus.OK);
	}
	
}

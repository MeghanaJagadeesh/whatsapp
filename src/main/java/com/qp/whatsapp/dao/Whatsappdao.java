package com.qp.whatsapp.dao;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.qp.whatsapp.dto.WhatsappNumbers;
import com.qp.whatsapp.repository.WhatsappRepository;

@Component
public class Whatsappdao {

	@Autowired
	WhatsappRepository repository;
	
	public void saveWhatsappNumber(WhatsappNumbers numbers) {
		repository.save(numbers);
	}

	public List<WhatsappNumbers> fetchAllWhatsapp() {
		return repository.findAll();
		
	}

	public WhatsappNumbers find(String whatsappId) {
		return repository.findById(Integer.parseInt(whatsappId)).get();
		
	}
}

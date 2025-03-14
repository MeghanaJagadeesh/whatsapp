package com.qp.whatsapp.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class WhatsappNumbers {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;
	private String whatsappName;
	private String whatsappId;
	private String accesstoken;
	private String phoneNumberId;
	
}

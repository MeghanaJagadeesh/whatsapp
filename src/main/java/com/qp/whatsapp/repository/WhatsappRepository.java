package com.qp.whatsapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.qp.whatsapp.dto.WhatsappNumbers;

@Repository
public interface WhatsappRepository extends JpaRepository<WhatsappNumbers , Integer>{

}

package com.qp.whatsapp.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

@Service
public class FileUploader {

	private static final int SFTP_PORT = 22;
	private static final String SFTP_USER = "dh_gmj3vr";
	private static final String SFTP_PASSWORD = "Srikrishna@0700";
	private static final String SFTP_HOST = "pdx1-shared-a2-03.dreamhost.com";
	private static final String SFTP_DIRECTORY = "/home/dh_gmj3vr/mantramatrix.in/whatsapp_analytics/";
	private static final String BASE_URL = "https://mantramatrix.in/whatsapp_analytics/";
	private final ExecutorService executorService = Executors.newFixedThreadPool(10);

	public String handleFileUpload(File file) {
		System.out.println("handle file");
		try {
			byte[] fileBytes = Files.readAllBytes(file.toPath());
			;
			String originalFilename = file.getName();
			String uniqueFileName = generateUniqueFileName(originalFilename);

			// Submit upload task
			CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
				return uploadFileViaSFTP(fileBytes, uniqueFileName);
			}, executorService);

			try {
				String fileUrl = future.get();
				if (fileUrl != null) {
					System.out.println("file url"+fileUrl);
					return fileUrl;
				} else {
					return null;
				}
			} catch (Exception e) {
				return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private String uploadFileViaSFTP(byte[] fileBytes, String fileName) {
		JSch jsch = new JSch();
		Session session = null;
		ChannelSftp sftpChannel = null;

		try {
			session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
			session.setPassword(SFTP_PASSWORD);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();

			sftpChannel = (ChannelSftp) session.openChannel("sftp");
			sftpChannel.connect();

			try (InputStream fileInputStream = new ByteArrayInputStream(fileBytes)) {
				sftpChannel.put(fileInputStream, SFTP_DIRECTORY + fileName);
			}
			return BASE_URL + fileName;
		} catch (JSchException | SftpException | IOException e) {
			e.printStackTrace();
			System.out.println("Error uploading file: " + e.getMessage());
			return null;
		} finally {
			if (sftpChannel != null) {
				try {
					sftpChannel.disconnect();
				} catch (Exception e) {
					System.out.println("Error disconnecting SFTP channel: " + e.getMessage());
				}
			}
			if (session != null) {
				try {
					session.disconnect();
				} catch (Exception e) {
					System.out.println("Error disconnecting SFTP session: " + e.getMessage());
				}
			}
		}
	}

	private String generateUniqueFileName(String originalFilename) {
		String extension = "";
		int dotIndex = originalFilename.lastIndexOf(".");
		if (dotIndex >= 0) {
			extension = originalFilename.substring(dotIndex);
		}
		return UUID.randomUUID().toString() + extension;
	}
}

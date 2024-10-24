package com.example.demo;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
 
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
/*

package com.example.demo.controller;

import com.example.demo.service.DrawImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Controller
public class DrawImageController {

    @Autowired
    private DrawImageService drawImageService;

    @GetMapping("/drawImage")
    @ResponseBody  // 추가 필요
    public ResponseEntity<InputStreamResource> drawImage(@RequestParam String filePath) throws IOException {
        byte[] imageBytes = drawImageService.createImage(filePath);

        // 이미지 응답 설정
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"image.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(new ByteArrayInputStream(imageBytes)));
    }
}


package com.example.demo.service;

import java.io.IOException;

public interface DrawImageService {
    byte[] createImage(String filePath) throws IOException;
}


package com.example.demo.service.impl;

import com.example.demo.service.DrawImageService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DrawImageServiceImpl implements DrawImageService {
    private static final int IMAGE_SIZE = 32;

    @Override
    public byte[] createImage(String filePath) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 배경을 검정색으로 설정
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);

        // 좌표 파일을 읽고 도형 그리기
        try (BufferedReader br = new BufferedReader(new FileReader(new File(filePath)))) {
            List<String[]> elements = br.lines()
                    .map(line -> line.split(","))
                    .collect(Collectors.toList());

            for (String[] element : elements) {
                String shapeType = element[0].trim().toLowerCase();  // 도형 유형 (circle, rect, line, ellipse)
                switch (shapeType) {
                    case "circle":
                        drawCircle(g2d, element);
                        break;
                    case "rect":
                        drawRect(g2d, element);
                        break;
                    case "line":
                        drawLine(g2d, element);
                        break;
                    case "ellipse":
                        drawEllipse(g2d, element);  // 타원형 처리
                        break;
                    default:
                        System.out.println("Unknown shape: " + shapeType);
                }
            }
        }

        g2d.dispose();

        // 이미지 바이트 배열로 변환
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    // 원 그리기
    private void drawCircle(Graphics2D g2d, String[] element) {
        int x = Integer.parseInt(element[1].trim());
        int y = Integer.parseInt(element[2].trim());
        int radius = Integer.parseInt(element[3].trim());
        Color color = getColor(element[4].trim());

        g2d.setColor(color);
        g2d.drawOval(x - radius, y - radius, 2 * radius, 2 * radius);
    }

    // 사각형 그리기
    private void drawRect(Graphics2D g2d, String[] element) {
        int x = Integer.parseInt(element[1].trim());
        int y = Integer.parseInt(element[2].trim());
        int width = Integer.parseInt(element[3].trim());
        int height = Integer.parseInt(element[4].trim());
        Color color = getColor(element[5].trim());

        g2d.setColor(color);
        g2d.drawRect(x, y, width, height);
    }

    // 선 그리기
    private void drawLine(Graphics2D g2d, String[] element) {
        int x1 = Integer.parseInt(element[1].trim());
        int y1 = Integer.parseInt(element[2].trim());
        int x2 = Integer.parseInt(element[3].trim());
        int y2 = Integer.parseInt(element[4].trim());
        Color color = getColor(element[5].trim());

        g2d.setColor(color);
        g2d.drawLine(x1, y1, x2, y2);
    }

    // 타원형 그리기
    private void drawEllipse(Graphics2D g2d, String[] element) {
        int x = Integer.parseInt(element[1].trim());
        int y = Integer.parseInt(element[2].trim());
        int radiusX = Integer.parseInt(element[3].trim());
        int radiusY = Integer.parseInt(element[4].trim());
        Color color = getColor(element[5].trim());

        g2d.setColor(color);
        g2d.drawOval(x - radiusX, y - radiusY, 2 * radiusX, 2 * radiusY);
    }

    // 색상 파싱
    private Color getColor(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red":
                return Color.RED;
            case "blue":
                return Color.BLUE;
            case "green":
                return Color.GREEN;
            case "yellow":
                return Color.YELLOW;
            default:
                return Color.WHITE;  // 기본 색상
        }
    }
}


*/

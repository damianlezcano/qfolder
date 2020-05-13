package com.example.pushserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication
public class SpringBootApp {

  public static void main(String[] args) {
    SpringApplication.run(SpringBootApp.class, args);
  }

}
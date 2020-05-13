package com.example.pushserver;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = { "/myServlet" })
public class MyServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/event-stream");
		response.setCharacterEncoding("UTF-8");

		PrintWriter writer = response.getWriter();

		while(!writer.checkError()) {

			System.out.println(this);
			writer.write("event:up_vote\n");
			//writer.write("data: \n\n");

			writer.flush();
			
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		writer.close();
		System.out.println("fin");
	}
}

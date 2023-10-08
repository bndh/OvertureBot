package org.example;

import net.dv8tion.jda.api.entities.Message;

import java.util.LinkedList;

public class VideoContainer {

	private LinkedList<Message.Attachment> attachments;
	private LinkedList<String> links;

	// TODO Consider adding a max size to the container class
	public VideoContainer() {
		attachments = new LinkedList<>();
		links = new LinkedList<>();
	}

	// TODO Are these by reference or by value?
	public VideoContainer(LinkedList<Message.Attachment> attachments, LinkedList<String> links) {
		this.attachments = attachments;
		this.links = links;
	}

	// TODO Is letting the user edit attachments and links outside of this improper encapsulation?
	public LinkedList<Message.Attachment> getAttachments() { return attachments; }
	public LinkedList<String> getLinks() { return links; }
	public int getSize() { return attachments.size() + links.size(); }

	// TODO Change to appendAttachments and appendVideos or have two methods with the same name that change depending on the parameters
	public void appendAttachment(Message.Attachment attachment) { attachments.addLast(attachment); }
	public void appendLink(String link) { links.addLast(link); }

}

package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumPost;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.example.ids.IDManager;
import org.example.launch.Launcher;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApplicationListener extends ListenerAdapter {

	private static final int MAX_APPLICATION_VIDEOS = 5;
	private static final int MAX_SESSION_DURATION = 600000; // In milliseconds
	private static final int MESSAGE_SCAN_LIMIT = 20;

	private final JDA api;
	private final Guild overture;
	private final File sessionIdFile;
	private final IDManager sessionManager;
	private ForumChannel applicationChannel;

	// TODO Send application button
	// TODO Sort out countdown timer
	// TODO Cool-down timer on applying
	// TODO Anonymous applications
	// TODO Desired rank option?
	// TODO Cancel feature
	// TODO Add admin commands
	// TODO Convert program to nanoseconds

	public ApplicationListener(JDA api, Guild overture) {
		this.api = api;
		this.overture = overture;
		sessionIdFile = new File(Launcher.LOCAL_FILE_PATHWAY + "ids/sessionIDs.txt");
		sessionManager = new IDManager(sessionIdFile);

		try { // Check for necessary channels
			applicationChannel = overture.getForumChannelsByName("applications", true).get(0); // The first channel named applications will become the bots designated channel
		} catch(IndexOutOfBoundsException e) { // No channels found
			applicationChannel = overture.createForumChannel("applications").complete();
		}
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		event.deferReply().setEphemeral(true).queue();

		if(event.getName().equals("apply")) {

			PrivateChannel userDm = event.getUser().openPrivateChannel().complete(); // Open DM with command user

			EmbedBuilder embedBuilder = getFormattedBuilder();
			embedBuilder.setTitle("Application process initiating...");
			embedBuilder.setDescription("Please wait.");
			Message applicationMessage = userDm.sendMessageEmbeds(embedBuilder.build()).complete();

			try {
				if(sessionManager.scanForId(userDm.getId())) {
					sessionManager.deleteId(userDm.getId());
				}
				sessionManager.appendEntry(new String[]{
						userDm.getId(),
						applicationMessage.getId(),
						String.valueOf(System.currentTimeMillis() + MAX_SESSION_DURATION)
				});

				embedBuilder.setTitle("Application process initiated.");
				embedBuilder.setDescription(
						"""
								Apply for a new role by sending messages here.
								A maximum of **5 links or files** may be attached.
								
								End your application by typing **"send"** or clicking the **send button**.
								If you exceed the maximum attachment limit, not all of your videos will be included.
								
								Your application can be cancelled early by typing **"cancel"** or clicking the **cancel button**.
						"""
				);
				embedBuilder.setThumbnail("https://upload.wikimedia.org/wikipedia/en/3/35/Geometry_Dash_Logo.PNG");
				applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();

				event.getHook().sendMessage( // Respond to the command in the original channel
						"Awaiting links or files to your gameplay in our DM!"
				).queue();
			} catch(IOException e) { // Abort
				embedBuilder.setTitle("Application process aborted.");
				embedBuilder.setDescription(
						"""
								Something went wrong...
								Please try again.
						"""
				);
				applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();

				event.getHook().sendMessage(
						"Something went wrong! Please try again."
				).queue();
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		// TODO Restrict possibilities more efficiently
		if(event.getChannelType() == ChannelType.PRIVATE && !event.getAuthor().isBot()) { // Whittle down the message possibilities
			PrivateChannel userDm = event.getChannel().asPrivateChannel();
			if(event.getMessage().getContentStripped().equalsIgnoreCase("send")) {
				try {
					String[] activeSession = sessionManager.readForId(userDm.getId());
					if(activeSession != null) {
						sendApplication(
								api.getPrivateChannelById(activeSession[0]).retrieveMessageById(activeSession[1]).complete()
						);
					}
				} catch (IOException | NullPointerException | ExecutionException | InterruptedException e) {
					EmbedBuilder embedBuilder = getFormattedBuilder();
					embedBuilder.setTitle("Application not sent!");
					embedBuilder.setDescription(
							"""
								Something went wrong...
								Please try again.
							"""
					);
					userDm.sendMessageEmbeds(embedBuilder.build()).queue();
					throw new RuntimeException(e);
				}
			}
		}
	}

	public void sendApplication(Message applicationMessage) throws InterruptedException, ExecutionException, IOException {
		PrivateChannel userDm = applicationMessage.getChannel().asPrivateChannel();
		String applicationMessageId = applicationMessage.getId();

		sessionManager.deleteId(applicationMessage.getChannel().getId()); // If we can't delete the session, we don't want to do anything else

		LinkedList<Message> messageHistory = new LinkedList<>();
		userDm.getIterableHistory().cache(false).forEachAsync(message -> {
			messageHistory.addLast(message);
			return messageHistory.size() < MESSAGE_SCAN_LIMIT && !message.getId().equals(applicationMessageId);
		}).get();

		LinkedList<String> videoLinks = new LinkedList<>();

		Pattern urlPattern = Pattern.compile("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"); // URL Regex
		for(Message message : messageHistory) {
			Matcher matcher = urlPattern.matcher(message.getContentStripped());
			while(matcher.find()) {
				videoLinks.addLast(matcher.group());
				if(videoLinks.size() >= MAX_APPLICATION_VIDEOS) break;
			}

			for(Message.Attachment attachment : message.getAttachments()) {
				videoLinks.add(attachment.getUrl());
				if(videoLinks.size() >= MAX_APPLICATION_VIDEOS) break;
			}
		}

		User user = userDm.getUser();
		String username = user.getEffectiveName();
		MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
		EmbedBuilder embedBuilder = getFormattedBuilder();
		embedBuilder.setTitle("New application from **" + username + "**");
		embedBuilder.setDescription("Waiting on feedback from a judge!");
		embedBuilder.setThumbnail(user.getAvatarUrl());
		messageCreateBuilder.addEmbeds(embedBuilder.build());

		ForumPost applicationPost = applicationChannel.createForumPost(username + "'s Application", messageCreateBuilder.build()).complete();
		ThreadChannel applicationThread = applicationPost.getThreadChannel();
		for(String link : videoLinks) {
			applicationThread.sendMessage(link).queue();
		}

		embedBuilder.clear();
		embedBuilder = getFormattedBuilder();
		embedBuilder.setTitle("Application submitted!");
		userDm.sendMessageEmbeds(embedBuilder.build()).queue();
	}

	public EmbedBuilder getFormattedBuilder() {
		EmbedBuilder embedBuilder = new EmbedBuilder();
		embedBuilder.setAuthor("Overture Systems");
		embedBuilder.setColor(Color.ORANGE);
		embedBuilder.setTimestamp(Instant.now());
		return embedBuilder;
	}

}
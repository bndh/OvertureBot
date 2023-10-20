package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
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
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApplicationListener extends ListenerAdapter {

	private static final int MAX_APPLICATION_VIDEOS = 5;
	private static final int MAX_SESSION_DURATION = 600000; // In milliseconds
	private static final int MESSAGE_SCAN_LIMIT = 20;

	private final JDA api;
	private final Guild overture;
	private File sessionIdFile;
	private final IDManager sessionFileManager;
	private ForumChannel overtureAppChannel;

	// TODO Is it wise to store all the applicationAttachments in here?
	// TODO Send application button
	// TODO Sort out countdown timer
	// TODO Failsafe if there is already an application session for that user
	// TODO Store attachment URL instead of attachment
	// TODO Review asynchronous processing
	// TODO Overwrite old applications with new ones
	// TODO Cool-down timer on applying
	// TODO Anonymous applications
	// TODO Desired rank option?
	// TODO Cancel feature
	// TODO Simultaneous appending to and deleting from a file
	// TODO Streams
	// TODO Function for creating a message with consistent styling
	// TODO Review code
	// TODO Add admin commands
	// TODO Ensure that the same channel cannot become a session twice

	public ApplicationListener(JDA api, Guild overture) {
		this.api = api;
		this.overture = overture;

		sessionIdFile = new File(Launcher.LOCAL_FILE_PATHWAY + "ids/sessionIDs.txt");
		sessionFileManager = new IDManager(sessionIdFile);

		try { // Check for necessary channels
			overtureAppChannel = overture.getForumChannelsByName("applications", true).get(0); // The first channel named applications will become the bots designated channel
		} catch(IndexOutOfBoundsException e) { // No channels found
			overtureAppChannel = overture.createForumChannel("applications").complete();
		}
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		event.deferReply().setEphemeral(true).queue();

		if(event.getName().equals("apply")) {

			// Notifying command user
			PrivateChannel userChannel = event.getUser().openPrivateChannel().complete(); // Open DM with command user

			EmbedBuilder eb = new EmbedBuilder(); // Build instructions and send in DM
			eb.setAuthor("Overture Application Team");
			eb.setColor(Color.ORANGE);
			eb.setDescription(
					"""
							Apply for a new role by sending messages here.
							A maximum of **5 links or files** may be attached.
							
							End your application by typing **"send"** or clicking the **send button**.
							If you exceed the maximum attachment limit, not all of your videos will be included.
							
							Your application can be cancelled early by typing **"cancel"** or clicking the **cancel button**.
					""");
			eb.setThumbnail("https://upload.wikimedia.org/wikipedia/en/3/35/Geometry_Dash_Logo.PNG");
			eb.setTitle("Application process initiated.");
			Message applicationMessage = userChannel.sendMessageEmbeds(eb.build()).complete();

			// Update ID file
			sessionFileManager.requestScan(	// TODO Fix abomination
					userChannel.getId()
			).thenAccept((found) -> {
				if(found) {
					sessionFileManager.requestDelete(
							userChannel.getId()
					).thenRun(() -> sessionFileManager.requestAppend(
							userChannel.getId() + ", "
									+ applicationMessage.getId() + ", "
									+ (System.currentTimeMillis() + MAX_SESSION_DURATION)
					));
				} else {
					sessionFileManager.requestAppend(
							userChannel.getId() + ", "
									+ applicationMessage.getId() + ", "
									+ (System.currentTimeMillis() + MAX_SESSION_DURATION)
					);
				}
			});

			// Respond to command
			event.getHook().sendMessage( // Respond to the command in the original channel
					"Awaiting links or files to your gameplay in our DM!"
			).queue();
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		// TODO Restrict possibilities more efficiently
		if(event.getChannelType() == ChannelType.PRIVATE && !event.getAuthor().isBot()) { // Whittle down the message possibilities
			PrivateChannel userChannel = event.getChannel().asPrivateChannel();
			if(event.getMessage().getContentStripped().equalsIgnoreCase("send")) {
				sessionFileManager.requestRead(
						userChannel.getId()
				).thenAccept((result) -> {
					if(result != null) { // Session found
						sendApplication(api.getPrivateChannelById(result[0]).retrieveMessageById(result[1]).complete());
					}
				});
			}
		}
	}

	public void sendApplication(Message applicationMessage) {
		PrivateChannel userDm = applicationMessage.getChannel().asPrivateChannel();

		// TODO This is counting the "send" as well
		// Amass message history
		LinkedList<Message> messages = new LinkedList<>();
		AtomicInteger counter = new AtomicInteger(0);
		try {

			userDm.getIterableHistory().cache(false).forEachAsync((message) -> {
				System.out.println("Message Read\nMessageContent: " + message.getContentStripped() + "\nMessageID: " + message.getId());
				if (!(counter.incrementAndGet() == MESSAGE_SCAN_LIMIT + 1) && !message.getId().equals(applicationMessage.getId())) { // Don't count the send command
					if (!message.getAuthor().isBot()) {
						System.out.println("Message added");
						messages.addLast(message);
					}
					return true;
				} else {
					return false;
				}
			}).get();
		} catch(InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		// Scan for links
		LinkedList<String> links = new LinkedList<>();
		for (Message message : messages) {
			System.out.println("New Message\nMessageContent: " + message.getContentStripped() + "\nMessageAttachmentSize: " + message.getAttachments().size());

			// Links
			// TODO Precompile this pattern (static?)
			Pattern pattern = Pattern.compile("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"); // URL Regex
			Matcher matcher = pattern.matcher(message.getContentStripped());
			while (matcher.find()) {
				links.addLast(matcher.group());
				if (links.size() >= MAX_APPLICATION_VIDEOS) break;
			}

			// Attachments
			for (Message.Attachment ma : message.getAttachments()) {
				links.add(ma.getUrl());
				if (links.size() >= MAX_APPLICATION_VIDEOS) break;
			}
		}
		System.out.println("Links.size(): " + links.size());
		// Application text and title
		MessageCreateBuilder mcb = new MessageCreateBuilder();
		EmbedBuilder eb = new EmbedBuilder();

		eb.setAuthor("Overture Application Team");
		eb.setColor(Color.ORANGE);
		eb.setTitle("New Application from **" + userDm.getUser().getEffectiveName() + "**");
		mcb.addEmbeds(eb.build());

		System.out.println("Got to pre-forum send");
		ForumPost applicationPost = overtureAppChannel.createForumPost(userDm.getUser().getEffectiveName() + "'s Application", mcb.build()).complete();
		System.out.println("Got to post-forum send");
		ThreadChannel applicationThread = applicationPost.getThreadChannel();
		for (String s : links) {
			applicationThread.sendMessage(s).queue();
		}
	}
}
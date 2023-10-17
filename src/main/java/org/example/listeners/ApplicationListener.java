package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.launch.Launcher;

import java.awt.*;
import java.io.*;

public class ApplicationListener extends ListenerAdapter {

	private static final int MAX_APPLICATION_VIDEOS = 5;
	private static final int MAX_SESSION_DURATION = 600000; // In milliseconds

	private final JDA api;
	private final Guild overture;
	private ForumChannel overtureAppChannel;

	// TODO Is it wise to store all the applicationAttachments in here?
	// TODO Send application button SICK
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

	public ApplicationListener(JDA api, Guild overture) {
		this.api = api;
		this.overture = overture;

		try { // Check for necessary channels
			overtureAppChannel = api.getForumChannelsByName("applications", true).get(0); // The first channel named applications will become the bots designated channel
		} catch(IndexOutOfBoundsException e) { // No channels found
			overtureAppChannel = overture.createForumChannel("applications").complete();
		}
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		event.deferReply().queue();

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

			// Update ID file and hashmap
			appendSessionEntry(applicationMessage.getId(), System.currentTimeMillis() + MAX_SESSION_DURATION); // The channel representing the application session and the time in millis when the session will expire

			// Respond to command
			event.getHook().sendMessage( // Respond to the command in the original channel
					"Awaiting links or files to your gameplay in our DM!"
					).setEphemeral(true).queue(); // TODO Investigate why messages are not ephemeral
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		// TODO Restrict possibilities more efficiently
		if(event.getChannelType() == ChannelType.PRIVATE && !event.getAuthor().isBot()) { // Whittle down the message possibilities
			PrivateChannel privateChannel = event.getChannel().asPrivateChannel();

//			if(activeSessions.containsKey(privateChannel)) { // If the message comes from a DM currently being treated as an application
//				VideoContainer applicationVideos = activeSessions.get(privateChannel);
//
//				if(event.getMessage().getContentStripped().equalsIgnoreCase("send")) { // Send application
//					if(applicationVideos.getSize() == 0) {
//						privateChannel.sendMessage("No videos attached!\nPlease add some videos before attempting to apply.").queue();
//					} else {
//						MessageCreateBuilder mcb = new MessageCreateBuilder();
//						EmbedBuilder eb = new EmbedBuilder();
//
//						// Generic StringBuilder formatting
//						eb.setAuthor("Overture Application Team");
//						eb.setColor(Color.ORANGE);
//
//						// Application text and title
//						eb.setTitle("New Application from **" + privateChannel.getUser().getEffectiveName() + "**");
//						StringBuilder sb = new StringBuilder();
//						for (String s : applicationVideos.getLinks()) sb.append(s).append("\n");
//						for (Message.Attachment a : applicationVideos.getAttachments()) sb.append(a.getUrl()).append("\n");
//						eb.setDescription(sb.toString());
//						mcb.addEmbeds(eb.build());
//
//						// Create application
//						overtureAppChannel.createForumPost("New Application!", mcb.build()).queue();
//
//						// Respond in DM
//						eb.setDescription("""
//										Your application has been sent!
//										Expect a reply from the application team in the coming days.
//										""");
//						eb.setTitle("Application process complete.");
//						privateChannel.sendMessageEmbeds(eb.build()).queue();
//
//						// Remove channel from active sessions
//						activeSessions.remove(privateChannel);
//					}
//				} else if(activeSessions.get(privateChannel).getSize() == MAX_APPLICATION_VIDEOS) { // Application videos full
//					 privateChannel.sendMessage("Maximum video limit reached!\nPlease remove some videos before attempting to attach more or some videos may be lost.").queue();
//				} else { // Scan for videos and update the hashmap
//					// TODO Streamline this
//					// Manage links
//					Pattern pattern = Pattern.compile("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"); // URL Regex
//					Matcher matcher = pattern.matcher(event.getMessage().getContentStripped());
//					while(matcher.find()) {
//						applicationVideos.appendLinks(matcher.group());
//						if(applicationVideos.getSize() >= MAX_APPLICATION_VIDEOS) break;
//					}
//
//					// Manage attachments
//					for(Message.Attachment a : event.getMessage().getAttachments()) {
//						applicationVideos.appendAttachments(a);
//						if(applicationVideos.getSize() >= MAX_APPLICATION_VIDEOS) break;
//					}
//				}
//			}
		}
	}

	public void sendApplication(Message applicationMessage) {
		PrivateChannel userDm = applicationMessage.getChannel().asPrivateChannel();
//		userDm.getIterableHistory().cache(false).forEachAsync((message) -> { // No need to update cache as we only need the history for this one action
//					if(!message.getAuthor().isBot() && !message.getId().equals(applicationMessage.getId())) {
//
//					}
//				});
	}

	public void appendSessionEntry(String messageId, long timeout) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(Launcher.LOCAL_FILE_PATHWAY + "ids/sessionIDs.txt", true));
			writer.append("\n")
					.append(messageId).append(", ")
					.append(String.valueOf(timeout));
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String[] getSessionEntry(String messageId) { // Returns the line in sessionIDs corresponding to the messageId or otherwise returns null if it is not found
		try {
			BufferedReader reader = new BufferedReader((new FileReader(Launcher.LOCAL_FILE_PATHWAY + "ids/sessionIDs.txt")));
			String line = reader.readLine(); // First line is populated with labels
			while((line = reader.readLine()) != null) {
				String[] lineArray = line.split(", ");
				if(line.split(", ")[0].equals(messageId)) {
					return lineArray;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}
}
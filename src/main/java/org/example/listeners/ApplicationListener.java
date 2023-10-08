package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.example.VideoContainer;

import java.awt.*;
import java.io.*;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApplicationListener extends ListenerAdapter {

	private static final String LOCAL_FILE_PATHWAY = "src/main/java/org/example/";
	private static final int MAX_APPLICATION_VIDEOS = 5;
	private static final int MAX_APPLICATION_TIME = 600000; // In milliseconds

	private final JDA api;
	private final Guild overture;
	private ForumChannel overtureAppChannel;
	private final HashMap<PrivateChannel, VideoContainer> activeSessions = new HashMap<>();

	// TODO Is it wise to store all the applicationAttachments in here?
	// TODO Send application button SICK
	// TODO Sort out countdown timer
	// TODO Failsafe if there is already an application session for that user
	// TODO Store attachment URL instead of attachment
	// TODO Review asynchronous processing
	// TODO Overwrite old applications with new ones
	// TODO Cool-down timer on applying

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
							
							End your application by typing **"send"**.
							If you exceed the maximum attachment limit, not all of your videos will be included.
					""");
			eb.setThumbnail("https://upload.wikimedia.org/wikipedia/en/3/35/Geometry_Dash_Logo.PNG");
			eb.setTitle("Application process initiated.");
			userChannel.sendMessageEmbeds(eb.build()).queue();

			// Update ID file and hashmap
			appendApplication(userChannel.getId(), System.currentTimeMillis() + MAX_APPLICATION_TIME);

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
			PrivateChannel eventChannel = event.getChannel().asPrivateChannel();
			VideoContainer applicationVideos = activeSessions.get(eventChannel);

			if(activeSessions.containsKey(eventChannel)) { // If the message comes from a DM currently being treated as an application
				if(event.getMessage().getContentStripped().equalsIgnoreCase("send")) { // Send application
					if(applicationVideos.getSize() == 0) {
						eventChannel.sendMessage("No videos attached!\nPlease add some videos before attempting to apply.").queue();
					} else {
						MessageCreateBuilder mcb = new MessageCreateBuilder();
						EmbedBuilder eb = new EmbedBuilder();

						// Generic StringBuilder formatting
						eb.setAuthor("Overture Application Team");
						eb.setColor(Color.ORANGE);

						// Application text and title
						eb.setTitle("New Application from **" + eventChannel.getUser().getEffectiveName() + "**");
						StringBuilder sb = new StringBuilder();
						for (String s : applicationVideos.getLinks()) sb.append(s).append("\n");
						for (Message.Attachment a : applicationVideos.getAttachments()) sb.append(a.getUrl()).append("\n");
						eb.setDescription(sb.toString());
						mcb.addEmbeds(eb.build());

						// Create application
						overtureAppChannel.createForumPost("New Application!", mcb.build()).queue();

						// Respond in DM
						eb.setDescription(
								"""
												Your application has been sent!
												Expect a reply from the application team in the coming days.
										""");
						eb.setTitle("Application process complete.");
						eventChannel.sendMessageEmbeds(eb.build()).queue();

						// Remove channel from active sessions
						activeSessions.remove(eventChannel);
					}
				} else if(activeSessions.get(eventChannel).getSize() == MAX_APPLICATION_VIDEOS) { // Application videos full
					 eventChannel.sendMessage("Maximum video limit reached!\nPlease remove some videos before attempting to attach more or some videos may be lost.").queue();
				} else { // Scan for videos and update the hashmap
					// TODO Streamline this
					// Manage links
					Pattern pattern = Pattern.compile("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"); // URL Regex
					Matcher matcher = pattern.matcher(event.getMessage().getContentStripped());
					while(matcher.find()) {
						applicationVideos.appendLink(matcher.group());
						if(applicationVideos.getSize() >= MAX_APPLICATION_VIDEOS) break;
					}

					// Manage attachments
					for(Message.Attachment a : event.getMessage().getAttachments()) {
						applicationVideos.appendAttachment(a);
						if(applicationVideos.getSize() >= MAX_APPLICATION_VIDEOS) break;
					}
				}
			}
		}
	}


	@Override
	public void onMessageDelete(MessageDeleteEvent event) {

	}

	public void appendApplication(String channelId, long timeout) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(LOCAL_FILE_PATHWAY + "ids/applicationIDs.txt", true));
			writer.append("\n")
					.append(channelId).append(", ")
					.append(String.valueOf(timeout));
			writer.close();
			activeSessions.put(api.getPrivateChannelById(channelId), new VideoContainer());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String[] scanAppFile(String channelId) {
		try {
			BufferedReader reader = new BufferedReader((new FileReader(LOCAL_FILE_PATHWAY + "ids/applicationIDs.txt")));
			String line = reader.readLine(); // First line is populated with labels
			while((line = reader.readLine()) != null) {
				String[] lineArray = line.split(", ");
				if(line.split(", ")[0].equals(channelId)) {
					return lineArray;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}
}
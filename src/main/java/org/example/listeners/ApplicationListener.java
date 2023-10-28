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
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.example.exceptions.RequestException;
import org.example.ids.IDManager;
import org.example.launch.Launcher;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApplicationListener extends ListenerAdapter {

	private static final int MAX_APPLICATION_VIDEOS = 5;
	private static final int MAX_SESSION_DURATION = 600000; // 10 minutes in milliseconds
	private static final int MESSAGE_SCAN_LIMIT = 20;

	private final JDA api;
	private final Guild overture;
	private final File sessionIdFile;
	private final IDManager sessionManager;
	private final Timer timeoutTimer;
	private final ArrayList<TimerTask> timerTasks;
	private ForumChannel applicationChannel;

	// TODO Cool-down timer on applying
	// TODO Anonymous applications
	// TODO Desired rank option?
	// TODO Add admin commands
	// TODO Convert program to nanoseconds
	// TODO Push application command
	// TODO Something went wrong function that auto-generates an embed with a reason as a parameter
	// TODO There is a bug if two people apply at the same exact time. Fixable with a special TimerContainer class
	// TODO Detailed applicant facts
	// TODO Edit send messages to have proper deferred replies
	// TODO Edit original message
	// TODO Report bug modal
	// TODO Reject application button (admins)
	// TODO Automate role and emoji identification based on name

	public ApplicationListener(JDA api, Guild overture) {
		this.api = api;
		this.overture = overture;
		sessionIdFile = new File(Launcher.LOCAL_FILE_PATHWAY + "ids/sessionIDs.txt");
		sessionManager = new IDManager(sessionIdFile);
		timeoutTimer = new Timer(true);
		timerTasks = new ArrayList<>();

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
			String userDmId = userDm.getId();

			EmbedBuilder embedBuilder = getStyledEmbedBuilder(EmbedStates.NEUTRAL);
			embedBuilder.setTitle("Application starting!");
			embedBuilder.setDescription("Please wait.");
			Message applicationMessage = userDm.sendMessageEmbeds(embedBuilder.build()).complete();

			try { // Append the session to the idFile.
				if(sessionManager.containsKeyId(userDmId)) { // Delete the session if it already exists
					sessionManager.deleteId(userDmId);
				}
				sessionManager.appendEntry(new String[]{
						userDmId,
						applicationMessage.getId(),
						String.valueOf(System.currentTimeMillis() + MAX_SESSION_DURATION)
				});

				TimerTask onTimeout = new TimerTask() { // Stop the application session on timeout
					@Override
					public void run() {
						try {
							sessionManager.deleteId(userDmId);
							embedBuilder.setColor(EmbedStates.FAILURE.getColor());
							embedBuilder.setTitle("Application timed out!");
							embedBuilder.setDescription("Your application session has expired.\nPlease try starting a new application.");
							applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();
						} catch(IOException e) {
							throw new RuntimeException(e);
						}
					}
				};
				timeoutTimer.schedule(onTimeout, MAX_SESSION_DURATION);
				timerTasks.add(onTimeout);

				embedBuilder.setTitle("Application process initiated!");
				embedBuilder.setDescription(
						"""
						Apply for a new role by sending messages here.
						A maximum of **5 links or files** may be attached.
								
						End your application by typing **"send"** or clicking the **send button**.
						If you exceed the **maximum attachment limit**, not all of your videos will be included.
								
						Your application can be cancelled early by typing **"cancel"** or clicking the **cancel button**.
						Once an application has been sent, it **cannot** be cancelled.
						"""
				);
				embedBuilder.setThumbnail("https://upload.wikimedia.org/wikipedia/en/3/35/Geometry_Dash_Logo.PNG");
				applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();
				applicationMessage.editMessageComponents(
						ActionRow.of(
								Button.success("send", "Send").withEmoji(Emoji.fromUnicode("U+2705")),
								Button.danger("cancel", "Cancel").withEmoji(Emoji.fromUnicode("U+26D4"))
						)
				).queue();

				event.getHook().sendMessage("Check your DM!").queue();
			} catch(IOException e) { // Abort
				embedBuilder.setColor(EmbedStates.FAILURE.getColor());
				embedBuilder.setTitle("Application process aborted!");
				embedBuilder.setDescription("Something went wrong.\nPlease try again.");
				applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();

				event.getHook().sendMessage("Something went wrong! Please try again.").queue();
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
				processSendRequest(userDm);
			} else if(event.getMessage().getContentStripped().equalsIgnoreCase("cancel")) {
				processCancelRequest(userDm);
			}
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		if(event.getChannelType() == ChannelType.PRIVATE) { // Whittle down the message possibilities
			event.deferEdit().queue(); // Prevents the button from telling the user the interaction failed
			PrivateChannel userDm = event.getChannel().asPrivateChannel();

			LinkedList<Button> disabledButtons = new LinkedList<>();
			Message applicationMessage = event.getMessage();
			for(Button button : applicationMessage.getButtons()) {
				disabledButtons.add(button.asDisabled());
			}
			applicationMessage.editMessageComponents(ActionRow.of(disabledButtons)).queue();

			if(event.getButton().getId().equals("send")) {
				processSendRequest(userDm);
			} else if(event.getButton().getId().equals("cancel")) {
				processCancelRequest(userDm);
			}
		}
	}

	public void processSendRequest(PrivateChannel userDm) {
		EmbedBuilder embedBuilder = getStyledEmbedBuilder();
		try {
			String[] sessionData = sessionManager.readForId(userDm.getId());
			if(sessionData != null) {
				sendApplication(userDm.retrieveMessageById(sessionData[1]).complete());
			} else {
				embedBuilder.setColor(EmbedStates.FAILURE.getColor());
				embedBuilder.setTitle("Application not sent!");
				embedBuilder.setDescription("This channel is not an active application.\nPlease use Overture's **/apply** command to start one.");
				userDm.sendMessageEmbeds(embedBuilder.build()).queue();
			}
		} catch(IOException | ExecutionException | InterruptedException e) {
			embedBuilder.setColor(EmbedStates.FAILURE.getColor());
			embedBuilder.setTitle("Application process aborted!");
			embedBuilder.setDescription("Something went wrong.\nPlease try again.");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
			throw new RequestException("Error during application send!");
		}
	}

	public void processCancelRequest(PrivateChannel userDm) {
		try {
			deleteSession(userDm);

			EmbedBuilder embedBuilder = getStyledEmbedBuilder(EmbedStates.FAILURE);
			embedBuilder.setTitle("Application process cancelled!");
			embedBuilder.setDescription("You cancelled the application.\nYou may apply again at any time.");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
		} catch(IOException e) {
			throw new RequestException("Error during application cancel!");
		}
	}

	public void sendApplication(Message applicationMessage) throws InterruptedException, ExecutionException, IOException {
		PrivateChannel userDm = applicationMessage.getChannel().asPrivateChannel();
		String applicationMessageId = applicationMessage.getId();

		deleteSession(userDm);

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

		EmbedBuilder embedBuilder = getStyledEmbedBuilder();
		if(videoLinks.size() == 0) {
			embedBuilder.setColor(EmbedStates.FAILURE.getColor());
			embedBuilder.setTitle("Application not sent!"); // TODO This could be made more user-friendly by not forcing them to restart the application.
			embedBuilder.setDescription("No files were attached!\nPlease restart your application. ");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
		} else {
			User user = userDm.getUser();
			String username = user.getEffectiveName();

			MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
			embedBuilder.setColor(EmbedStates.SUCCESS.getColor());
			embedBuilder.setTitle("New application!");
			embedBuilder.addField("User", username, true);
			embedBuilder.addField("Current Rank", overture.getRoleById("1167253083634544730").getName(), true);
			embedBuilder.setDescription(
     				"""
					Waiting on feedback from a judge!
					Select the role that should be rewarded from the dropdown.
					Feel free to share opinions in the thread before submitting.
					"""
			);
			embedBuilder.setThumbnail(user.getAvatarUrl());
			messageCreateBuilder.addEmbeds(embedBuilder.build());

			ForumPost applicationPost = applicationChannel.createForumPost(username + "'s Application", messageCreateBuilder.build())
					.addActionRow(
							StringSelectMenu.create("role")
									.addOption("Godly Layout Creator", "godly", Emoji.fromFormatted("<:godlc:1167592347702394992>"))
									.addOption("Incredible Layout Creator", "incredible", Emoji.fromFormatted("<:inlc:1167592386768154665>"))
									.addOption("Amazing Layout Creator", "amazing", Emoji.fromFormatted("<:amlc:1167592442736955413>"))
									.addOption("Good Layout Creator", "good", Emoji.fromFormatted("<:glc:1167592481223876648>"))
									.addOption("Average Layout Creator", "average", Emoji.fromFormatted("<:avlc:1167592660316459008>"))
									.addOption("Beginner Layout Creator", "beginner", Emoji.fromFormatted("<:blc:1167592777257865256>"))
									.build()
					).complete();
			ThreadChannel applicationThread = applicationPost.getThreadChannel();
			for(String link : videoLinks) {
				applicationThread.sendMessage(link).queue();
			}

			embedBuilder.clear();
			embedBuilder = getStyledEmbedBuilder(EmbedStates.SUCCESS);
			embedBuilder.setTitle("Application submitted!");
			embedBuilder.setDescription("");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
		}
	}

	public enum EmbedStates {
		SUCCESS(new Color(67, 181, 129)),
		NEUTRAL(Color.ORANGE),
		FAILURE(new Color(240, 71, 71));

		private final Color color;
		EmbedStates(Color color) { this.color = color; }
		public Color getColor() { return color;}
	}

	public EmbedBuilder getStyledEmbedBuilder(EmbedStates embedState, String thumbnailLink) {
		EmbedBuilder embedBuilder = new EmbedBuilder();
		embedBuilder.setAuthor("Overture Systems");
		embedBuilder.setTimestamp(Instant.now());
		embedBuilder.setColor(embedState.getColor());
		if(thumbnailLink != null) embedBuilder.setThumbnail(thumbnailLink);
		return embedBuilder;
	}

	public EmbedBuilder getStyledEmbedBuilder(EmbedStates embedState) {
		return getStyledEmbedBuilder(embedState, null);
	}

	public EmbedBuilder getStyledEmbedBuilder() {
		return getStyledEmbedBuilder(EmbedStates.NEUTRAL, null);
	}

	public void deleteSession(PrivateChannel userDm) throws IOException {
		String userDmId = userDm.getId();
		cancelTimer(sessionManager.readForId(userDmId)[2]);
		sessionManager.deleteId(userDmId);
	}

	public void cancelTimer(String timeoutMillis) {
		for(TimerTask timerTask : timerTasks) {
			if(timerTask.scheduledExecutionTime() == Long.parseLong(timeoutMillis)) {
				timerTask.cancel();
				break;
			}
		}
	}

}
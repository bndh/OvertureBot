package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumPost;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.example.exceptions.RequestException;
import org.example.ids.idmanagers.IDManager;
import org.example.ids.idmanagers.TimedIDManager;
import org.example.launch.Launcher;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApplicationListener extends ListenerAdapter {

	private static final int MAX_APPLICATION_VIDEOS = 5;
	private static final int MAX_SESSION_DURATION = 10000; // 600000; // 10 minutes in milliseconds
	private static final int MESSAGE_SCAN_LIMIT = 20;

	private final JDA api;
	private final Guild overture;
	private final TimedIDManager sessionManager;
	private final IDManager rankManager;
	private final Timer timeoutTimer;
	private final ArrayList<TimerTask> applicationTimeouts;
	private ForumChannel applicationChannel;

	// TODO Read in pre-existing timers and set the roleIDs file
	// TODO Cool-down timer on applying
	// TODO Anonymous applications
	// TODO Desired rank option?
	// TODO Add admin commands (roleIDs will be set by this)
	// TODO Convert program to nanoseconds
	// TODO Push application command
	// TODO Something went wrong function that auto-generates an embed with a reason as a parameter
	// TODO There is a bug if two people apply at the same exact time. Fixable with a special TimerContainer class or just checking for other applications at the same time.
	// TODO Streamline responses with replies and edits, etc. Make it consistent.
	// TODO Report bug modal
	// TODO Reject application button (admins)
	// TODO Automate role and emoji identification based on name
	// TODO Keep the original application message as an application tracker
	// TODO Should - at the very least - automatically delete roles from memory if their role gets deleted
	// TODO Check timer tasks on boot
	// TODO Automatically find the id files on boot?
	// TODO Maybe make a error cases file or something that I can read the information from for all of my embedBuilders
	// TODO Check out the files class for one time use things

	public ApplicationListener(JDA api, Guild overture) {
		this.api = api;
		this.overture = overture;
		timeoutTimer = new Timer(true);
		applicationTimeouts = new ArrayList<>();

		sessionManager = new TimedIDManager(
				(Launcher.LOCAL_FILE_PATHWAY + "ids/sessionIDs.txt"),
				entryArray -> {
					EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.FAILURE);
					embedBuilder.setTitle("Application timed out!");
					embedBuilder.setDescription("Your application session has expired.\nPlease try starting a new application.");

					Message applicationMessage = api.getPrivateChannelById(entryArray[0]).retrieveMessageById(entryArray[1]).complete();
					LinkedList<Button> disabledButtons = new LinkedList<>();
					for(Button button : applicationMessage.getButtons()) {
						disabledButtons.add(button.asDisabled());
					}
					applicationMessage.editMessageComponents(ActionRow.of(disabledButtons)).queue();
					applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();
				},
				0,
				2
		);
		rankManager = new IDManager(Launcher.LOCAL_FILE_PATHWAY + "ids/roleIDs.txt", 0);

		try { // Account for timers from last boot
			for(String[] entryArray : sessionManager.dump(false)) {
				sessionManager.startExpiryTimer(entryArray);
			}
		} catch(IOException e) {
			e.printStackTrace();
		}

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

			EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.NEUTRAL);
			embedBuilder.setTitle("Application starting!");
			embedBuilder.setDescription("Please wait.");
			Message applicationMessage = userDm.sendMessageEmbeds(embedBuilder.build()).complete();

			try { // Append the session to the idFile.
				if (sessionManager.containsKey(userDmId)) { // Delete the session if it already exists
					sessionManager.deleteEntry(userDmId);
				}
				sessionManager.appendEntry(new String[]{
						userDmId,
						applicationMessage.getId(),
						String.valueOf(System.currentTimeMillis() + MAX_SESSION_DURATION)
				});

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
			} catch (IOException e) { // Abort
				embedBuilder.setColor(Launcher.EmbedStates.FAILURE.getColor());
				embedBuilder.setTitle("Application process aborted!");
				embedBuilder.setDescription("Something went wrong.\nPlease try again.");
				applicationMessage.editMessageEmbeds(embedBuilder.build()).queue();

				event.getHook().sendMessage("Something went wrong! Please try again.").queue();
				throw new RuntimeException(e);
			}
		} else if(event.getName().equals("addlc")) {
			// TODO Try using futures here and catch less haphazardly
			try {
				Message.Attachment iconAttachment = event.getOption("icon").getAsAttachment();
				String emojiName = event.getOption("emoji-name").getAsString();
				String roleName = event.getOption("role-name").getAsString();
				String roleHex = event.getOption("role-hex").getAsString();

				Icon icon = iconAttachment.getProxy().downloadAsIcon().get();
				CustomEmoji rankEmoji = overture.createEmoji(emojiName, icon).complete();
				Role rankRole = overture.createRole()
						.setName(roleName)
						.setColor(new Color(
								Integer.valueOf(roleHex.substring(0, 2), 16), // Hex to RGB
								Integer.valueOf(roleHex.substring(2, 4), 16),
								Integer.valueOf(roleHex.substring(4, 6), 16)))
						//.setIcon(icon)
						.setMentionable(true)
				.complete();

				rankManager.appendEntry(new String[]{
						rankRole.getId(),
						rankEmoji.getId()
				});

				EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.SUCCESS);
				embedBuilder.setTitle("Role Creation Successful!");
				embedBuilder.setDescription("The role and emoji were created successfully.\nThey have also been loaded into the bot's storage.");
				event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
			} catch(ExecutionException | InterruptedException | NullPointerException | IOException e) {
				EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.FAILURE);
				embedBuilder.setTitle("Role Creation Failed!");
				embedBuilder.setDescription("Something went wrong! PLease try again.");
				event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
			}
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
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

	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		event.deferReply().queue();

		List<String> values = event.getInteraction().getValues();
		String roleId = values.get(0);
		Role role = overture.getRoleById(roleId);
		String applicantId = event.getInteraction().getSelectMenu().getId();

		overture.addRoleToMember(UserSnowflake.fromId(applicantId), role).queue();

		EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.SUCCESS);
		embedBuilder.setTitle("Result sent!");
		embedBuilder.setDescription("The applicant was awarded **" + role.getName() + "**");
		event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
	}

	protected void processSendRequest(PrivateChannel userDm) {
		EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder();
		try {
			String[] sessionData = sessionManager.readForEntry(userDm.getId());
			if(sessionData != null) {
				sendApplication(userDm.retrieveMessageById(sessionData[1]).complete());
			} else {
				embedBuilder.setColor(Launcher.EmbedStates.FAILURE.getColor());
				embedBuilder.setTitle("Application not sent!");
				embedBuilder.setDescription("This channel is not an active application.\nPlease use Overture's **/apply** command to start one.");
				userDm.sendMessageEmbeds(embedBuilder.build()).queue();
			}
		} catch(IOException | ExecutionException | InterruptedException e) {
			embedBuilder.setColor(Launcher.EmbedStates.FAILURE.getColor());
			embedBuilder.setTitle("Application process aborted!");
			embedBuilder.setDescription("Something went wrong.\nPlease try again.");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
			throw new RequestException("Error during application send!");
		}
	}

	protected void processCancelRequest(PrivateChannel userDm) {
		try {
			sessionManager.deleteEntry(userDm.getId());

			EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.FAILURE);
			embedBuilder.setTitle("Application process cancelled!");
			embedBuilder.setDescription("You cancelled the application.\nYou may apply again at any time.");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
		} catch(IOException e) {
			throw new RequestException("Error during application cancel!");
		}
	}

	protected void sendApplication(Message applicationMessage) throws InterruptedException, ExecutionException, IOException, NullPointerException {
		PrivateChannel userDm = applicationMessage.getChannel().asPrivateChannel();
		User user = userDm.getUser();
		String username = user.getEffectiveName();
		String applicationMessageId = applicationMessage.getId();

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

		sessionManager.deleteEntry(userDm.getId()); // The only destructive operation so we do it last

		// At this point no exceptions should be thrown
		EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder();
		if(videoLinks.size() == 0) {
			embedBuilder.setColor(Launcher.EmbedStates.FAILURE.getColor());
			embedBuilder.setTitle("Application not sent!"); // TODO This could be made more user-friendly by not forcing them to restart the application.
			embedBuilder.setDescription("No files were attached!\nPlease restart your application. ");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
		} else {
			MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
			embedBuilder.setColor(Launcher.EmbedStates.SUCCESS.getColor());
			embedBuilder.setTitle("New application!");
			embedBuilder.addField("User", username, true);
			embedBuilder.addField("Current Rank", overture.getRoleById("1173424788803440783").getName(), true); // TODO Fix this (no guarantee they have a rank)
			embedBuilder.setDescription(
     				"""
					Waiting on feedback from a judge!
					Select the role that should be rewarded from the dropdown.
					Feel free to share opinions in the thread before submitting.
					"""
			);
			embedBuilder.setThumbnail(user.getAvatarUrl());
			messageCreateBuilder.addEmbeds(embedBuilder.build());

			StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(user.getId()); // Allows us to reference the user in the string selection event
			for(String[] entryArray : rankManager.dump(false)) { // TODO THIS IS VERY SLOW
				menuBuilder.addOption(overture.getRoleById(entryArray[0]).getName(), entryArray[0], Emoji.fromFormatted(overture.retrieveEmojiById(entryArray[1]).complete().getFormatted()));
			}

			ForumPost applicationPost = applicationChannel.createForumPost(username + "'s Application", messageCreateBuilder.build())
					.addActionRow(
							menuBuilder.build()
					).complete();
			ThreadChannel applicationThread = applicationPost.getThreadChannel();
			for(String link : videoLinks) {
				applicationThread.sendMessage(link).queue();
			}

			embedBuilder.clear();
			embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.SUCCESS);
			embedBuilder.setTitle("Application submitted!");
			embedBuilder.setDescription("");
			userDm.sendMessageEmbeds(embedBuilder.build()).queue();
		}
	}

}
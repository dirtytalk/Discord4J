/*
 * Discord4J - Unofficial wrapper for Discord API
 * Copyright (c) 2015
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package sx.blah.discord;

import org.junit.Test;
import sx.blah.discord.api.*;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.InviteReceivedEvent;
import sx.blah.discord.handle.impl.events.MessageDeleteEvent;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.obj.Invite;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.MessageBuilder;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * General testing bot. Also a demonstration of how to use the bot.
 */
public class TestBot {
	
	private static final String CI_URL = "https://drone.io/github.com/austinv11/Discord4J/";
	private static final long MAX_TEST_TIME = 120000L;
	
	@Test(timeout = 300000L)
	public void testBot() {
		main(System.getenv("USER"), System.getenv("PSW"), "CITest");
	}
	
	/**
	 * Starts the bot. This can be done any place you want.
	 * The main method is for demonstration.
	 *
	 * @param args Command line arguments passed to the program.
	 */
	public static void main(String... args) {
		try {
			IDiscordClient client = new ClientBuilder().withLogin(args[0] /* username */, args[1] /* password */).build();
			if (args.length > 2) { //CI Testing
				Discord4J.LOGGER.debug("CI Test Initiated");
				Discord4J.LOGGER.debug("Discord API has a response time of {}ms", DiscordStatus.getAPIResponseTimeForDay());
				
				for (DiscordStatus.Maintenance maintenance : DiscordStatus.getUpcomingMaintenances()) {
					Discord4J.LOGGER.warn("Discord has upcoming maintenance: {} on {}", maintenance.getName(), maintenance.getStart().toString());
				}
				
				client.login();
				
				final AtomicBoolean didTest = new AtomicBoolean(false);
				client.getDispatcher().registerListener(new IListener<ReadyEvent>() {
					@Override
					public void handle(ReadyEvent readyEvent) {
						try {
							//Initialize required data
							IInvite testInvite = client.getInviteForCode(System.getenv("INVITE").replace("https://discord.gg/", ""));
							Invite.InviteResponse response = testInvite.accept();
							IInvite spoofInvite = client.getInviteForCode(System.getenv("SPOOF_INVITE").replace("https://discord.gg/", ""));
							Invite.InviteResponse spoofResponse = spoofInvite.accept();
							final IChannel testChannel = client.getChannelByID(response.getChannelID());
							final IChannel spoofChannel = client.getChannelByID(spoofResponse.getChannelID());
							String buildNumber = System.getenv("BUILD_ID");
							
							//Start testing
							new MessageBuilder(client).withChannel(testChannel).withContent("Initiating Discord4J Unit Tests for Build #"+
									buildNumber, MessageBuilder.Styles.BOLD).build();
							
							//Clearing spoofbot's mess from before
							synchronized (client) {
								for (IMessage message : spoofChannel.getMessages()) {
									message.delete();
								}
							}
							
							//Time to unleash the ai
							SpoofBot spoofBot = new SpoofBot(client, System.getenv("SPOOF"), System.getenv("PSW"), System.getenv("SPOOF_INVITE"));
							
							final long now = System.currentTimeMillis();
							new Thread(()->{
								while (!didTest.get()) {
									if (now+MAX_TEST_TIME <= System.currentTimeMillis()) {
										//Test timer up!
										synchronized (client) {
											new MessageBuilder(client).withChannel(testChannel).withContent("Success! The build is complete. See the log here: "+CI_URL+buildNumber,
													MessageBuilder.Styles.BOLD).build();
										}
										didTest.set(true);
									}
								}
							}).start();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				
				while (!didTest.get()) {}
				
			} else { //Dev testing
				client.login();
				
				client.getDispatcher().registerListener(new IListener<MessageReceivedEvent>() {
					@Override
					public void handle(MessageReceivedEvent messageReceivedEvent) {
						IMessage m = messageReceivedEvent.getMessage();
						if (m.getContent().startsWith(".meme")
								|| m.getContent().startsWith(".nicememe")) {
							new MessageBuilder(client).appendContent("MEMES REQUESTED:", MessageBuilder.Styles.UNDERLINE_BOLD_ITALICS)
									.appendContent(" http://niceme.me/").withChannel(messageReceivedEvent.getMessage().getChannel())
									.build();
						} else if (m.getContent().startsWith(".clear")) {
							IChannel c = client.getChannelByID(m.getChannel().getID());
							if (null != c) {
								c.getMessages().stream().filter(message->message.getAuthor().getID()
										.equalsIgnoreCase(client.getOurUser().getID())).forEach(message->{
									try {
										Discord4J.LOGGER.debug("Attempting deletion of message {} by \"{}\" ({})", message.getID(), message.getAuthor().getName(), message.getContent());
										message.delete();
									} catch (MissingPermissionsException | HTTP429Exception | DiscordException e) {
										e.printStackTrace();
									}
								});
							}
						} else if (m.getContent().startsWith(".name ")) {
							String s = m.getContent().split(" ", 2)[1];
							try {
								client.changeAccountInfo(Optional.of(s), Optional.empty(), Optional.empty(), Optional.of(IDiscordClient.Image.forUser(client.getOurUser())));
								m.reply("is this better?");
							} catch (HTTP429Exception | MissingPermissionsException | DiscordException e) {
								e.printStackTrace();
							}
						} else if (m.getContent().startsWith(".pm")) {
							try {
								IPrivateChannel channel = client.getOrCreatePMChannel(m.getAuthor());
								new MessageBuilder(client).withChannel(channel).withContent("SUP DUDE").build();
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else if (m.getContent().startsWith(".presence")) {
							client.updatePresence(!client.getOurUser().getPresence().equals(Presences.IDLE),
									client.getOurUser().getGame());
						} else if (m.getContent().startsWith(".game")) {
							String game = m.getContent().length() > 6 ? m.getContent().substring(6) : null;
							client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE),
									Optional.ofNullable(game));
						} else if (m.getContent().startsWith(".type")) {
							m.getChannel().toggleTypingStatus();
						} else if (m.getContent().startsWith(".invite")) {
							try {
								m.reply("http://discord.gg/"+m.getChannel().createInvite(1800, 0, false, false).getInviteCode());
							} catch (MissingPermissionsException | HTTP429Exception | DiscordException e) {
								e.printStackTrace();
							}
						} else if (m.getContent().startsWith(".avatar")) {
							try {
								if (m.getContent().split(" ").length > 1) {
									String url = m.getContent().split(" ")[1];
									client.changeAccountInfo(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(IDiscordClient.Image.forUrl(url.substring(url.lastIndexOf('.')), url)));
								} else {
									client.changeAccountInfo(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(IDiscordClient.Image.defaultAvatar()));
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else if (m.getContent().startsWith(".permissions")) {
							if (m.getMentions().size() < 1)
								return;
							StringJoiner roleJoiner = new StringJoiner(", ");
							StringJoiner permissionsJoiner = new StringJoiner(", ");
							for (IRole role : m.getMentions().get(0).getRolesForGuild(m.getChannel().getGuild().getID())) {
								Discord4J.LOGGER.info("{}", role.getID());
								for (Permissions permissions : role.getPermissions()) {
									permissionsJoiner.add(permissions.toString());
								}
								roleJoiner.add(role.getName()+" ("+permissionsJoiner.toString()+")");
								permissionsJoiner = new StringJoiner(", ");
							}
							try {
								Discord4J.LOGGER.info("{}", m.getAuthor().getID());
								m.reply("This user has the following roles and permissions: "+roleJoiner.toString());
							} catch (MissingPermissionsException | HTTP429Exception | DiscordException e) {
								e.printStackTrace();
							}
						} else if (m.getContent().startsWith(".test")) {
							try {
								IGuild guild = client.createGuild("Test2", Optional.empty(), Optional.empty());
							} catch (HTTP429Exception | DiscordException e) {
								e.printStackTrace();
							}
						}
					}
				});
				
				client.getDispatcher().registerListener(new IListener<InviteReceivedEvent>() {
					@Override
					public void handle(InviteReceivedEvent event) {
						IInvite invite = event.getInvite();
						try {
							Invite.InviteResponse response = invite.details();
							event.getMessage().reply(String.format("you've invited me to join #%s in the %s guild!", response.getChannelName(), response.getGuildName()));
							invite.accept();
							client.getChannelByID(invite.details().getChannelID()).sendMessage(String.format("Hello, #%s and the \"%s\" guild! I was invited by %s!",
									response.getChannelName(), response.getGuildName(), event.getMessage().getAuthor()));
						} catch (Exception e) {
							e.printStackTrace();
						}
						
					}
				});
				
				client.getDispatcher().registerListener(new IListener<MessageDeleteEvent>() {
					@Override
					public void handle(MessageDeleteEvent event) {
						try {
							event.getMessage().reply("you said, \""+event.getMessage().getContent()+"\"");
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.*;

public class M extends Canvas {

	private final int scaleFactor = 4;
	private final int[] events = new int[32767];

	// Client Packets:
	// [ID] + (int)[pos] -> Set block
	// 0xFE -> Request player id
	// 0xFF -> Request the entire world
	private Socket server;

	private ServerSocket clientAcceptor;
	private List<Socket> clients = new ArrayList<>();
	private List<Socket> getClients() { clients.removeIf(Socket::isClosed); return clients; }

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setSize(856, 480);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		M game = new M();
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(game, "Center");
		frame.setContentPane(panel);

		JFrame selectFrame = new JFrame("Join/Host 4k");
		JPanel selectPanel = new JPanel();
		JTextField ipField = new JTextField("127.0.0.1:25575", 16);
		JButton joinButton = new JButton("Join");
		joinButton.addActionListener(e -> {
			try {
				String[] ips = ipField.getText().split(":");
				game.server = new Socket(ips[0], Integer.parseInt(ips[1]));
				frame.setTitle("Minecraft 4K CLIENT " + ipField.getText());

				selectFrame.setVisible(false);
				frame.setVisible(true);
				game.start(frame);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});
		JButton hostButton = new JButton("Host");
		hostButton.addActionListener(e -> {
			try {
				game.clientAcceptor = new ServerSocket(25575);
				game.server = new Socket("127.0.0.1", 25575);
				frame.setTitle("Minecraft 4K HOSTING");

				selectFrame.setVisible(false);
				frame.setVisible(true);
				game.start(frame);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		});

		selectPanel.add(ipField);
		selectPanel.add(joinButton);
		selectPanel.add(hostButton);
		selectFrame.add(selectPanel);
		selectFrame.setSize(260, 100);
		selectFrame.setResizable(false);
		selectFrame.setLocationRelativeTo(null);
		selectFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		selectFrame.setVisible(true);
	}

	public void start(JFrame frame) {
		if (clientAcceptor != null) {
			new Thread(() -> {
				try {
					while (true) {
						clients.add(clientAcceptor.accept());
						System.out.println("Accepted " + clients.get(clients.size() - 1));
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).start();
		}

		new Thread(() -> {
			try {
				Random random = new Random(18295169L);

				// 1: GRASS
				// 2: DIRT
				// 3: STONE
				// 4: BRICK
				// 5: LOG
				// 6: LEAVES
				int[] texturePixels = new int[196608/*256x16x48*/];

				for (int texId = 1; texId < 256; ++texId) {
					int brightness = 255 - random.nextInt(96);

					// y0-15 = top, y16-31 = side, y32-47 bottom
					for (int y = 0; y < 48; ++y) {
						for (int x = 0; x < 16; ++x) {
							int baseColor = 0x966C4A;
							if (texId == 3) {
								baseColor = 0x7F7F7F;
							} else if (texId >= 8) {
								baseColor = (128 + 127 * (texId - 7 & 1)) | (128 + 127 * (texId - 7 >> 1 & 1)) << 8 | (128 + 127 * (texId - 7 >> 2 & 1)) << 16;
							}

							if (texId != 3 || random.nextInt(3) == 0) {
								brightness = 255 - random.nextInt(96);
							}

							if (texId == 1 && y < (x * x * 3 + x * 81 >> 2 & 3) + 18) {
								baseColor = 0x6AAA40;
							} else if (texId == 1 && y < (x * x * 3 + x * 81 >> 2 & 3) + 19) {
								brightness = brightness * 2 / 3;
							}

							if (texId == 5) {
								baseColor = 0x675231;
								if (x > 0 && x < 15 && (y > 0 && y < 15 || y > 32 && y < 47)) {
									baseColor = 0xBC9862;
									int i2 = x - 7;
									int i3 = (y & 15) - 7;
									if (i2 < 0) {
										i2 = 1 - i2;
									}

									if (i3 < 0) {
										i3 = 1 - i3;
									}

									if (i3 > i2) {
										i2 = i3;
									}

									brightness = 196 - random.nextInt(32) + i2 % 3 * 32;
								} else if (random.nextInt(2) == 0) {
									brightness = brightness * (150 - (x & 1) * 100) / 100;
								}
							}

							if (texId == 4) {
								baseColor = 0xB53A15;
								if ((x + y / 4 * 4) % 8 == 0 || y % 4 == 0) {
									baseColor = 0xBCAFA5;
								}
							}

							if (y >= 32) {
								brightness = brightness / 2;
							}

							if (texId == 6) {
								baseColor = 0x50D937;
								if (random.nextInt(2) == 0) {
									baseColor = 0;
									brightness = 255;
								}
							}

							int color = (baseColor >> 16 & 0xFF) * brightness / 255 << 16 |
										(baseColor >> 8 & 0xFF) * brightness / 255 << 8 |
										(baseColor & 0xFF) * brightness / 255;
							texturePixels[x + y * 16 + texId * 256 * 3] = color;
						}
					}
				}

				int[] world = new int[262144/*64x64x64*/];

				if (clientAcceptor != null) {
					for (int i = 0; i < 262144; ++i) {
						world[i] = i / 64 % 64 > 32/* + random.nextInt(8)*/ ? random.nextInt(7) + 1 : 0;
					}
				} else {
					server.getOutputStream().write(0xff);
					server.getOutputStream().flush();
					for (int i = 0; i < world.length; i++) {
						world[i] = server.getInputStream().read();
					}
				}

				int playerId = 8;
				int maxPlayerId = playerId;
				if (clientAcceptor == null) {
					server.getOutputStream().write(0xFE);
					server.getOutputStream().flush();
					playerId = server.getInputStream().read() & 255;
				}

				float playerX = 32.5F;
				float playerY = 0.0F;
				float playerZ = 32.5F;
				float velocityX = 0.0F;
				float velocityY = 0.0F;
				float velocityZ = 0.0F;
				long lastFrameTime = System.currentTimeMillis();
				int rayBlock = -1;
				int rayDirection = 0;
				// Both yaw and pitch is in radians
				float playerYaw = 0.0F;
				float playerPitch = 0.0F;
				int selectedBlock = 1;

				while (true) {
					int gameWidth = frame.getWidth() / scaleFactor;
					int gameHeight = frame.getHeight() / scaleFactor;
					BufferedImage frameImage = new BufferedImage(gameWidth, gameHeight, BufferedImage.TYPE_INT_RGB);
					int[] framePixels = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();

					float sinYaw = (float) Math.sin(playerYaw);
					float cosYaw = (float) Math.cos(playerYaw);
					float sinPitch = (float) Math.sin(playerPitch);
					float cosPitch = (float) Math.cos(playerPitch);

					label271:
					while (System.currentTimeMillis() - lastFrameTime > 10L) {
						if (this.events[2] > 0) {
							float mouseX = ((float) this.events[2] / frame.getWidth() - 0.5f) * 2;
							float mouseY = ((float) this.events[3] / frame.getHeight() - 0.5f) * 2;
							float mouseDelta = (float) Math.sqrt(mouseX * mouseX + mouseY * mouseY) - 0.2F;
							if (mouseDelta < 0.0F) {
								mouseDelta = 0.0F;
							}

							if (mouseDelta > 0.0F) {
								playerYaw += mouseX * mouseDelta / 25.0F;
								playerPitch -= mouseY * mouseDelta / 25.0F;
								if (playerPitch < -1.57F) {
									playerPitch = -1.57F;
								}

								if (playerPitch > 1.57F) {
									playerPitch = 1.57F;
								}
							}
						}

						lastFrameTime += 10L;
						float inputForwards = 0.0F;
						float inputSideways = 0.0F;
						inputSideways += (float) (this.events[0x77/*w*/] - this.events[0x73/*s*/]) * 0.02F;
						inputForwards += (float) (this.events[0x64/*d*/] - this.events[0x61/*a*/]) * 0.02F;
						velocityX *= 0.5F;
						velocityY *= 0.99F;
						velocityZ *= 0.5F;
						velocityX += sinYaw * inputSideways + cosYaw * inputForwards;
						velocityZ += cosYaw * inputSideways - sinYaw * inputForwards;
						velocityY += 0.003F;

						for (int axis = 0; axis < 3; ++axis) {
							float newPlayerX = playerX + velocityX * (float) ((axis + 0) % 3 / 2);
							float newPlayerY = playerY + velocityY * (float) ((axis + 1) % 3 / 2);
							float newPlayerZ = playerZ + velocityZ * (float) ((axis + 2) % 3 / 2);

							for (int i = 0; i < 12; ++i) {
								int playerCornerX = (int) (newPlayerX + (float) (i & 1) * 0.6F - 0.3F);
								int playerCornerY = (int) (newPlayerY + (float) ((i >> 2) - 1) * 0.8F + 0.65F);
								int playerCornerZ = (int) (newPlayerZ + (float) (i >> 1 & 1) * 0.6F - 0.3F);
								if (playerCornerX < 0 || playerCornerY < 0 || playerCornerZ < 0 || playerCornerX >= 64 || playerCornerY >= 64 || playerCornerZ >= 64 || world[playerCornerX + playerCornerY * 64 + playerCornerZ * 4096] > 0) {
									if (axis == 1) {
										if (this.events[32] > 0 && velocityY > 0.0F) {
											this.events[32] = 0;
											velocityY = -0.1F;
										} else {
											velocityY = 0.0F;
										}
									}
									continue label271;
								}
							}

							int prevPos = (int) playerX + (int) playerY * 64 + (int) playerZ * 4096;
							int newPos = (int) newPlayerX + (int) newPlayerY * 64 + (int) newPlayerZ * 4096;
							if (prevPos != newPos) {
								server.getOutputStream().write(new byte[] {0,
										(byte) (prevPos & 255), (byte) (prevPos >> 8 & 255), (byte) (prevPos >> 16 & 255), (byte) (prevPos >> 24 & 255)});
								server.getOutputStream().write(new byte[] {(byte) playerId,
										(byte) (newPos & 255), (byte) (newPos >> 8 & 255), (byte) (newPos >> 16 & 255), (byte) (newPos >> 24 & 255)});
								server.getOutputStream().flush();
							}
							playerX = newPlayerX;
							playerY = newPlayerY;
							playerZ = newPlayerZ;
						}
					}

					for (int i = 48; i <= 55; i++) {
						if (this.events[i] > 0) {
							selectedBlock = i - 47;
						}
					}

					if (this.events[0] > 0 && rayBlock > 0) {
						//world[rayBlock] = 0;
						this.events[0] = 0;

						server.getOutputStream().write(new byte[] { 0,
								(byte) (rayBlock & 255), (byte) (rayBlock >> 8 & 255), (byte) (rayBlock >> 16 & 255), (byte) (rayBlock >> 24 & 255) });
						server.getOutputStream().flush();
					}

					if (this.events[1] > 0 && rayBlock > 0) {
						// world[rayBlock + rayDirection] = 1;
						this.events[1] = 0;

						server.getOutputStream().write(new byte[] { (byte) selectedBlock,
								(byte) (rayBlock + rayDirection & 255), (byte) (rayBlock + rayDirection >> 8 & 255), (byte) (rayBlock + rayDirection >> 16 & 255), (byte) (rayBlock + rayDirection >> 24 & 255) });
						server.getOutputStream().flush();
					}

					for (int i = 0; i < 12; ++i) {
						int playerCornerX = (int) (playerX + (float) (i & 1) * 0.6F - 0.3F);
						int playerCornerY = (int) (playerY + (float) ((i >> 2) - 1) * 0.8F + 0.65F);
						int playerCornerZ = (int) (playerZ + (float) (i >> 1 & 1) * 0.6F - 0.3F);
						if (playerCornerX >= 0 && playerCornerY >= 0 && playerCornerZ >= 0 && playerCornerX < 64 && playerCornerY < 64 && playerCornerZ < 64) {
							world[playerCornerX + playerCornerY * 64 + playerCornerZ * 4096] = 0;
						}
					}

					float i8 = -1.0F;

					for (int gameX = 0; gameX < gameWidth; ++gameX) {
						float fovYaw = (float) (gameX - gameWidth / 2) / 90.0F;

						for (int gameY = 0; gameY < gameHeight; ++gameY) {
							float fovPitch = (float) (gameY - gameHeight / 2) / 90.0F;
							float f22 = cosPitch + fovPitch * sinPitch;
							float f23 = fovPitch * cosPitch - sinPitch;
							float f24 = fovYaw * cosYaw + f22 * sinYaw;
							float f25 = f22 * cosYaw - fovYaw * sinYaw;
							int baseColor = 0;
							int brightness = 255;
							double closest = 20.0;
							float f26 = 5.0F;

							for (int axis = 0; axis < 3; ++axis) {
								float axisAngle = f24;
								if (axis == 1) {
									axisAngle = f23;
								}

								if (axis == 2) {
									axisAngle = f25;
								}

								float axisDistortion = 1.0F / (axisAngle < 0.0F ? -axisAngle : axisAngle);
								float f29 = f24 * axisDistortion;
								float f30 = f23 * axisDistortion;
								float f31 = f25 * axisDistortion;
								float f32 = playerX - (float) ((int) playerX);
								if (axis == 1) {
									f32 = playerY - (float) ((int) playerY);
								}

								if (axis == 2) {
									f32 = playerZ - (float) ((int) playerZ);
								}

								if (axisAngle > 0.0F) {
									f32 = 1.0F - f32;
								}

								float dist = axisDistortion * f32;
								float f34 = playerX + f29 * f32;
								float f35 = playerY + f30 * f32;
								float f36 = playerZ + f31 * f32;
								if (axisAngle < 0.0F) {
									if (axis == 0) {
										--f34;
									}

									if (axis == 1) {
										--f35;
									}

									if (axis == 2) {
										--f36;
									}
								}

								while (dist < closest) {
									int blockX = (int) f34;
									int blockY = (int) f35;
									int blockZ = (int) f36;
									if (blockX < 0 || blockY < 0 || blockZ < 0 || blockX >= 64 || blockY >= 64 || blockZ >= 64) {
										break;
									}

									int pos = blockX + blockY * 64 + blockZ * 4096;
									int blockId = world[pos];
									if (blockId > 0) {
										int i6 = (int) ((f34 + f36) * 16.0F) & 15;
										int i7 = ((int) (f35 * 16.0F) & 15) + 16;
										if (axis == 1) {
											i6 = (int) (f34 * 16.0F) & 15;
											i7 = (int) (f36 * 16.0F) & 15;
											if (f30 < 0.0F) {
												i7 += 32;
											}
										}

										int i26 = 0xFFFFFF;
										if (pos != rayBlock || i6 > 0 && i7 % 16 > 0 && i6 < 15 && i7 % 16 < 15) {
											i26 = texturePixels[i6 + i7 * 16 + blockId * 256 * 3];
										}

										if (dist < f26 && gameX == this.events[2] / scaleFactor && gameY == this.events[3] / scaleFactor) {
											i8 = (float) pos;
											rayDirection = 1;
											if (axisAngle > 0.0F) {
												rayDirection = -1;
											}

											rayDirection <<= 6 * axis;
											f26 = dist;
										}

										if (i26 > 0) {
											baseColor = i26;
											brightness = 255 - (int) (dist / 20.0F * 255.0F);
											brightness = brightness * (255 - (axis + 2) % 3 * 50) / 255;
											closest = dist;
										}
									}

									f34 += f29;
									f35 += f30;
									f36 += f31;
									dist += axisDistortion;
								}
							}

							int red = (baseColor >> 16 & 0xFF) * brightness / 255;
							int green = (baseColor >> 8 & 0xFF) * brightness / 255;
							int blue = (baseColor & 0xFF) * brightness / 255;
							framePixels[gameX + gameY * gameWidth] = red << 16 | green << 8 | blue;
						}
					}

					rayBlock = (int) i8;
					Thread.sleep(2L);

					this.getGraphics().drawImage(frameImage, 0, 0, frame.getWidth(), frame.getHeight(), null);

					// If we're the server do server management things
					if (clientAcceptor != null) {
						for (Socket client : getClients()) {
							if (client.getInputStream().available() >= 1) {
								int status = client.getInputStream().read();
								if (status == 0xFF) {
									System.out.println(client + " << 0xFF - Sending client world");
									for (int b: world) {
										client.getOutputStream().write(b);
									}
									client.getOutputStream().flush();
									System.out.println(client + " - 0xFF - Sent client world");
								} else if (status == 0xFE) {
									System.out.println(client + " << 0xFE - Sending player id " + (maxPlayerId + 1));
									client.getOutputStream().write(++maxPlayerId);
									client.getOutputStream().flush();
								} else {
									byte[] data = new byte[4];
									for (int i = 0; i < data.length; i++) {
										data[i] = (byte) client.getInputStream().read();
									}

									for (Socket client2 : getClients()) {
										if (status >= 8 && client2 == client) {
											continue; // Don't send the player model block back to the client it belongs to
										}
										client2.getOutputStream().write(status);
										client2.getOutputStream().write(data);
										client2.getOutputStream().flush();
									}
								}
							}
						}
					}

					// Client Handling
					if (server.getInputStream().available() >= 5) {
						int status = server.getInputStream().read();
						int pos = 0;
						for (int i = 0; i < 4; i++) {
							pos |= (server.getInputStream().read() & 255) << (i * 8);
						}

						world[pos] = status;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	public boolean handleEvent(Event event) {
		int i = 0;
		switch (event.id) {
			case Event.KEY_PRESS:
				i = 1;
			case Event.KEY_RELEASE:
				this.events[event.key] = i;
				break;
			case Event.MOUSE_DOWN:
				i = 1;
				this.events[2] = event.x;
				this.events[3] = event.y;
			case Event.MOUSE_UP:
				if ((event.modifiers & Event.META_MASK) > 0) {
					this.events[1] = i;
				} else {
					this.events[0] = i;
				}
				break;
			case Event.MOUSE_MOVE:
			case Event.MOUSE_DRAG:
				this.events[2] = event.x;
				this.events[3] = event.y;
				break;
			case Event.MOUSE_EXIT:
				this.events[2] = 0;
		}

		return true;
	}
}
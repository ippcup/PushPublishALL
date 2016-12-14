/*
 * This code is licensed with an MIT License Copyright (c) 2016: ippcupttocs
 * and leverages components of (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * Wowza is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 * of which I have no idea how to call out appropriately in here.
 * This and AutoGenerateSMIL.java were my first foray into application development
 * and the first attempt at writting something in Java with zero training...
 * enjoy!
 @author Scott
 */
package org.mycompany.wowza.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wowza.util.HTTPUtils;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.pushpublish.protocol.rtmp.PushPublishRTMP;
import com.wowza.wms.pushpublish.protocol.rtmp.PushPublishRTMPAuthProviderAdobe;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamActionNotify2;
import com.wowza.wms.vhost.IVHost;
//PushPublishALL, it RTMP publishes all configured streams from to each edge server so you don't have to!
//It also checks the connection count from each edge server and removes certain high bit-rate streams based on 2 configurable thresholds.

public class PushPublishALL extends ModuleBase {
	//
	private Map<IMediaStream, List<PushPublishRTMP>> publishers = new HashMap<IMediaStream, List<PushPublishRTMP>>();
	private Map<IMediaStream, PushPublishRTMP> publisherSingle = new HashMap<IMediaStream, PushPublishRTMP>();
	private ArrayList<String> pollingThreadName = new ArrayList<String>();
	//
	private int hostPort = 1935;
	private int checkInt = 60000;
	private int removeThreshold1 = 500;
	private int removeThreshold2 = 1000;
	private String streamNameThreshold1 = "_360p";
	private String streamNameThreshold2 = "_288p|_360p";
	private String edgeApp = null;
	private String[] edgeApps = null;
	private String[] edgeServers = null;
	private String[] monitoredEdgeServers = null;
	private String edgeServer = null;
	private String streamList = null;
	private String streamSource = null;
	private String streamUser = null;
	private String streamPass = null;
	private String appName = null;
	private Boolean isAdaptive = null;
	private Boolean isSendOrgTC = null;
	private String orgTcThreshold = null;
	private Boolean isDebugLog = false;
	private Boolean isDebugPackets = false;

	//
	class StreamNotify implements IMediaStreamActionNotify2 {
		//
		public void onPlay(IMediaStream stream, String streamName,
				double playStart, double playLen, int playReset) {
		}

		//
		public void onPause(IMediaStream stream, boolean isPause,
				double location) {
		}

		//
		public void onSeek(IMediaStream stream, double location) {
		}

		//
		public void onStop(IMediaStream stream) {
		}

		//
		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {
		}

		//
		public void onPauseRaw(IMediaStream stream, boolean isPause,
				double location) {
		}

		// onPublish
		public void onPublish(IMediaStream stream, String streamName,
				boolean isRecord, boolean isAppend) {
			WMSLogger log = WMSLoggerFactory.getLogger(null);
			// match published stream against configured settings
			// pushStreamSource pushStreamNames in Application.xml
			Pattern streamPattern = Pattern.compile(streamSource + "("
					+ streamList + ")");
			Matcher streamMatch = streamPattern.matcher(streamName);
			IApplicationInstance appInstance = stream.getStreams()
					.getAppInstance();
			try {
				// check if published stream matches
				if (streamMatch.find()) {
					// if multiple hosts and multiple apps
					if (edgeServers != null && edgeApps != null) {
						synchronized (publishers) {
							List<PushPublishRTMP> publisherList = new ArrayList<PushPublishRTMP>();
							log.debug("PushPublishAll#StreamNotify.onPublish(): multiple hosts and multiple apps");
							// setup the publisherList
							for (int i = 0; i < edgeServers.length; i++) {
								for (int j = 0; j < edgeApps.length; j++) {
									PushPublishRTMP publisher = new PushPublishRTMP();
									String dstHost = edgeServers[i].toString();
									String dstApplication = edgeApps[j]
											.toString();
									startPublisher(publisher, appInstance,
											dstHost, dstApplication,
											streamName, log);
									publisherList.add(publisher);
									log.info("PushPublishAll#StreamNotify.onPublish(): connect to: rtmp://"
											+ dstHost
											+ ":"
											+ hostPort
											+ "/"
											+ dstApplication
											+ "/_definst_/"
											+ streamName);

								}
							}
							publishers.put(stream, publisherList);
							// TODO maybe add multicast RTP support?
							// List<PushPublishRTP> rtppublisherList = new
							// ArrayList<PushPublishRTP>();
							// PushPublishRTP rtppublisher = new
							// PushPublishRTP();
							// rtppublisherList.add(rtppublisher);
							return;
						}
					}

					// if multiple hosts and single app
					else if (edgeServers != null && edgeApp != null) {
						synchronized (publishers) {
							List<PushPublishRTMP> publisherList = new ArrayList<PushPublishRTMP>();
							log.debug("PushPublishAll#StreamNotify.onPublish(): multiple hosts and single app");
							for (int i = 0; i < edgeServers.length; i++) {
								PushPublishRTMP publisher = new PushPublishRTMP();
								String dstHost = edgeServers[i].toString();
								String dstApplication = edgeApp.toString();
								startPublisher(publisher, appInstance, dstHost,
										dstApplication, streamName, log);
								publisherList.add(publisher);
								log.info("PushPublishAll#StreamNotify.onPublish(): connect to: rtmp://"
										+ dstHost
										+ ":"
										+ hostPort
										+ "/"
										+ dstApplication
										+ "/_definst_/"
										+ streamName);

							}
							publishers.put(stream, publisherList);
							return;
						}
					}

					// if single host and multiple apps
					else if (edgeServer != null && edgeApps != null) {
						synchronized (publishers) {
							List<PushPublishRTMP> publisherList = new ArrayList<PushPublishRTMP>();
							log.debug("PushPublishAll#StreamNotify.onPublish(): single host and multiple apps");
							// PushPublishRTMP publisher = new
							// PushPublishRTMP();
							// setup the publisherList
							for (int j = 0; j < edgeApps.length; j++) {
								PushPublishRTMP publisher = new PushPublishRTMP();
								String dstHost = edgeServer.toString();
								String dstApplication = edgeApps[j].toString();
								startPublisher(publisher, appInstance, dstHost,
										dstApplication, streamName, log);
								publisherList.add(publisher);
								log.info("PushPublishAll#StreamNotify.onPublish(): connect to: rtmp://"
										+ dstHost
										+ ":"
										+ hostPort
										+ "/"
										+ dstApplication
										+ "/_definst_/"
										+ streamName);
							}
							// publishes the streams
							publishers.put(stream, publisherList);
							return;
						}
					}

					// if single host and single app
					else if (edgeServer != null && edgeApp != null) {
						synchronized (publisherSingle) {
							PushPublishRTMP publisher = new PushPublishRTMP();
							log.debug("PushPublishAll#StreamNotify.onPublish(): single host and single app");
							String dstHost = edgeServer.toString();
							String dstApplication = edgeApp.toString();
							startPublisher(publisher, appInstance, dstHost,
									dstApplication, streamName, log);
							log.info("PushPublishAll#StreamNotify.onPublish(): connect to: rtmp://"
									+ dstHost
									+ ":"
									+ hostPort
									+ "/"
									+ dstApplication
									+ "/_definst_/"
									+ streamName);
							publisherSingle.put(stream, publisher);
						}
					}

					else {
						log.warn("PushPublishAll#StreamNotify.onPublish(): unable to determine suitable publishing method. Check Application.xml. edgeServers;edgeApps;edgeServer;edgeApp values: "
								+ edgeServers
								+ ";"
								+ edgeApps
								+ ";"
								+ edgeServer + ";" + edgeApp);
					}
				} else {
					log.debug("PushPublishAll#StreamNotify.onPublish(): REGEX match failed for streamName: "
							+ streamName
							+ " using pattern: "
							+ streamPattern.toString());
				}
			} catch (Exception e) {
				log.error("PushPublishAll#StreamNotify.onPublish(): Exception: "
						+ e.toString());
			}
			startGetThread(stream, log);
		}

		// onUnPublished
		public void onUnPublish(IMediaStream stream, String streamName,
				boolean isRecord, boolean isAppend) {
			boolean thresholdNotMet = false;
			WMSLogger log = WMSLoggerFactory.getLogger(null);
			log.info("PushPublishALL.onUnPublish(): streamName:" + streamName);
			stopPublisher(stream, streamName, thresholdNotMet, null, log);
		}

	}

	// Non-StreamNotify
	public PushPublishRTMP startPublisher(PushPublishRTMP publisher,
			IApplicationInstance appInstance, String dstHost,
			String dstApplication, String streamName, WMSLogger log) {

		String flashVersion = PushPublishRTMP.CURRENTFMLEVERSION;
		try {
			// Source Stream
			publisher.setAppInstance(appInstance);
			publisher.setSrcStreamName(streamName);
			publisher.setPort(hostPort);
			publisher.setDstStreamName(streamName);
			publisher.setConnectionFlashVersion(flashVersion);
			publisher.setAdaptiveStreaming(isAdaptive);
			publisher.setSendOriginalTimecodes(isSendOrgTC);
			publisher.setOriginalTimecodeThreshold(orgTcThreshold);
			publisher.setSendFCPublish(true);
			publisher.setSendReleaseStream(true);
			publisher.setSendOnMetadata(true);
			publisher.setDebugLog(isDebugLog);
			publisher.setDebugPackets(isDebugPackets);
			publisher.setHost(dstHost);
			publisher.setDstApplicationName(dstApplication);
			publisher.setConnectionTimeout(10000);
			if ((streamUser.length() > 1) && (streamPass.length() > 1)) {
				PushPublishRTMPAuthProviderAdobe adobeRTMPAuthProvider = new PushPublishRTMPAuthProviderAdobe();
				adobeRTMPAuthProvider.init(publisher);
				adobeRTMPAuthProvider.setUserName(streamUser);
				adobeRTMPAuthProvider.setPassword(streamPass);
				publisher.setRTMPAuthProvider(adobeRTMPAuthProvider);
				// publisher.setSecureTokenSharedSecret("someToken");
			} else {
				log.warn("PushPublishAll#StreamNotify.startPublisher(): streamUser/streamPass undefined in Application.xml");
			}

			publisher.connect();
		} catch (Exception e) {
			log.error("PushPublishAll#StreamNotify.startPublisher(): Exception: "
					+ e.toString());
		}
		return publisher;
	}

	// /_definst_/live/ Load on app startup
	public void onAppStart(IApplicationInstance appInstance) {
		WMSLogger log = WMSLoggerFactory.getLogger(null);
		try {
			WMSProperties props = appInstance.getProperties();
			appName = appInstance.getContextStr().split("/")[0];
			if (props.getPropertyStr("pushHosts", "") != null) {
				//
				this.checkInt = props
						.getPropertyInt("pushHostsCheckInt", 10000);
				this.removeThreshold1 = props.getPropertyInt(
						"pushRemoveThreshold1", 10);
				this.removeThreshold2 = props.getPropertyInt(
						"pushRemoveThreshold2", 20);
				//
				this.streamList = props.getPropertyStr(
						"pushStreamNamesThreshold0", "");
				this.streamNameThreshold1 = props.getPropertyStr(
						"pushStreamNamesThreshold1", "");
				this.streamNameThreshold2 = props.getPropertyStr(
						"pushStreamNamesThreshold2", "");
				//
				this.hostPort = props.getPropertyInt("pushHostsPort", 1935);
				this.streamSource = props
						.getPropertyStr("pushStreamSource", "");
				this.streamUser = props.getPropertyStr("pushStreamUser", "");
				this.streamPass = props.getPropertyStr("pushStreamPass", "");
				this.isAdaptive = props.getPropertyBoolean("pushIsAdaptive",
						false);
				this.isSendOrgTC = props.getPropertyBoolean(
						"pushSendOriginalTC", false);
				this.orgTcThreshold = props.getPropertyStr("pushTCThreshold",
						null);
				this.isDebugLog = props.getPropertyBoolean(
						"pushEnableisDebugLog", false);
				this.isSendOrgTC = props.getPropertyBoolean(
						"pushEnableisDebugPackets", false);
				// Single or multiple hosts
				if (props.getPropertyStr("pushHosts", "").indexOf(",") != -1) {
					this.edgeServers = (props.getPropertyStr("pushHosts", "")
							.toLowerCase()).split(",");
					for (int i = 0; i < edgeServers.length; i++) {
						log.info("PushPublishAll.onAppStart(): PushPublish dest hosts: "
								+ edgeServers[i].toString());
					}
					this.monitoredEdgeServers = (props.getPropertyStr(
							"pushHostsMonitored", "").toLowerCase()).split(",");
					for (int i = 0; i < monitoredEdgeServers.length; i++) {
						log.info("PushPublishAll.onAppStart(): PushPublish connectioninfo monitored edge server: "
								+ monitoredEdgeServers[i].toString());
					}
				} else {
					this.edgeServer = props.getPropertyStr("pushHosts", "")
							.toLowerCase();
					log.info("PushPublishAll.onAppStart(): PushPublish dest host: "
							+ edgeServer.toString());
				}
				// Single or multiple destination applications
				if (props.getPropertyStr("pushHostsAppName", "").indexOf(",") != -1) {
					this.edgeApps = (props.getPropertyStr("pushHostsAppName",
							"").toLowerCase()).split(",");
					for (int i = 0; i < edgeApps.length; i++) {
						log.debug("PushPublishAll.onAppStart(): PushPublish dest applications: "
								+ edgeApps[i].toString());
					}
				} else {
					this.edgeApp = props.getPropertyStr("pushHostsAppName", "")
							.toLowerCase();
					log.debug("PushPublishAll.onAppStart(): PushPublish dest application: "
							+ edgeApp.toString());
				}
				if ((isAdaptive = true) && (isSendOrgTC = true)
						&& (orgTcThreshold != null)) {
					log.debug("PushPublishAll.onAppStart(): "
							+ "All streams are pushed using adaptive bitrate settings");
				} else {
					log.debug("PushPublishAll.onAppStart(): "
							+ "All streams are NOT pushed using adaptive bitrate settings");
				}
			} else {
				log.error("PushPublishAll.onAppStart(): Application.xml properties are invalid");
			}

		} catch (Exception e) {
			log.error("PushPublishAll.onAppStart(): Exception: " + e.toString());
		}
	}

	// startup StreamNotify
	public void onStreamCreate(IMediaStream stream) {
		stream.addClientListener(new StreamNotify());
	}

	public void onStreamDestory(IMediaStream stream, String streamName) {

	}

	// custom
	public void stopPublisher(IMediaStream stream, String streamName,
			boolean thresholdMet, String edgeHost, WMSLogger log) {

		if (!thresholdMet) {
			Pattern streamPattern1 = Pattern.compile(streamSource + "("
					+ streamList + ")");
			Matcher streamMatch1 = streamPattern1.matcher(streamName);
			log.debug("PushPublishAll.stopPublisher(): stream: " + streamName);
			try {
				if (streamMatch1.find()) {
					if ((edgeServers != null) || (edgeApps != null)) {
						synchronized (publishers) {
							List<PushPublishRTMP> publisherList = publishers
									.remove(stream);
							for (PushPublishRTMP publisher : publisherList) {
								if (publisher != null) {
									log.info("PushPublishAll.stopPublisher(): stream:"
											+ streamName);
									publisher.disconnect();
								}
							}
						}
					}
					if ((edgeServer != null) && (edgeApp != null)) {
						synchronized (publisherSingle) {
							PushPublishRTMP publisher = publisherSingle
									.remove(stream);
							if (publisher != null) {
								publisher.disconnect();
							}
						}
					}
				} else {
					log.debug("PushPublishAll.stopPublisher(): REGEX match failed for streamName: "
							+ streamName
							+ " using pattern: "
							+ streamPattern1.toString());
				}
			} catch (Exception e) {
				if (e.toString() == "java.lang.NullPointerException") {
					log.debug("PushPublishAll.stopPublisher(): Exception: "
							+ e.toString() + " streamName:" + streamName
							+ " not found. Likely already disconnected");
				} else {
					log.error("PushPublishAll.stopPublisher(): Exception: "
							+ e.toString() + " streamName:" + streamName);
				}
			}
		} else if (thresholdMet) {
			try {
				if ((edgeServers != null) || (edgeApps != null)) {
					synchronized (publishers) {
						List<PushPublishRTMP> publisherList = publishers
								.get(stream);
						for (PushPublishRTMP publisher : publisherList) {
							if (publisher != null
									&& publisher.getHostname()
											.matches(edgeHost)
									&& publisher.getDstStreamName().matches(
											streamName)) {
								if (publishers.get(stream).contains(publisher)) {
									log.info("PushPublishAll.stopPublisher(): Threshold breached - Removing stream:"
											+ streamName
											+ " for host:"
											+ publisher.getHostname());
									publisher.disconnect();
									publishers.get(stream).remove(publisher);
								}
							}
						}
					}
				} else if ((edgeServer != null) && (edgeApp != null)) {
					synchronized (publisherSingle) {
						PushPublishRTMP publisher = publisherSingle
								.remove(stream);
						if (publisher != null
								&& publisher.getHostname().matches(edgeHost)
								&& publisher.getDstStreamName().matches(
										streamName)) {
							log.info("PushPublishAll.stopPublisher(): Threshold breached - Removing stream:"
									+ streamName
									+ " for host:"
									+ publisher.getHostname());
							publisher.disconnect();
						}
					}
				}
			} catch (Exception e) {
				if (e.toString() == "java.lang.NullPointerException") {
					log.debug("PushPublishAll.stopPublisher(): Exception: "
							+ e.toString() + " streamName:" + streamName
							+ " not found. Likely already disconnected");
				} else {
					log.error("PushPublishAll.stopPublisher(): Exception: "
							+ e.toString() + " streamName:" + streamName);
				}
			}
		}
	}

	// custom
	private void startGetThread(IMediaStream stream, WMSLogger log) {

		IApplicationInstance app = stream.getStreams().getAppInstance();
		final IVHost vhost = app.getVHost();
		if (monitoredEdgeServers != null
				&& pollingThreadName.size() < monitoredEdgeServers.length) {
			for (int i = 0; i < monitoredEdgeServers.length; i++) {
				final Integer initialDelay = i * 2000;
				final String edgeHost = monitoredEdgeServers[i].toString();
				log.info("PushPublishAll.startGetThread(): pollingThreadName:"
						+ pollingThreadName.size() + " monitoredEdgeServers:"
						+ monitoredEdgeServers.length
						+ " Creating new thread for edgeHost:" + edgeHost);

				if (!pollingThreadName.contains(edgeHost)) {
					pollingThreadName.add(edgeHost);
					Thread thread = new Thread(edgeHost) {
						public void run() {
							try {
								Thread.sleep(checkInt + initialDelay);

							} catch (InterruptedException e) {
								WMSLogger log = WMSLoggerFactory
										.getLogger(null);
								log.error("PushPublishAll.startGetThread().run(): Thread.sleep exception: "
										+ e.toString());
							}
							WMSLogger log = WMSLoggerFactory.getLogger(null);
							getEdgeConnInfo(edgeHost, vhost, log);
						}
					};
					if (!thread.isAlive()) {
						thread.start();
					}
				}
			}
		} else if (edgeServer != null && pollingThreadName.size() < 1) {
			final String edgeHost = edgeServer;
			log.info("PushPublishAll.startGetThread(): pollingThreadName:"
					+ pollingThreadName.size()
					+ " Creating new thread for single edgeHost:" + edgeHost);
			if (!pollingThreadName.contains(edgeHost)) {
				pollingThreadName.add(edgeHost);
				Thread thread = new Thread(edgeHost) {
					public void run() {
						try {
							Thread.sleep(checkInt);
						} catch (InterruptedException e) {
							WMSLogger log = WMSLoggerFactory.getLogger(null);
							log.error("PushPublishAll.startGetThread().run(): Thread.sleep exception: "
									+ e.toString());
						}
						WMSLogger log = WMSLoggerFactory.getLogger(null);
						getEdgeConnInfo(edgeHost, vhost, log);
					}
				};
				if (!thread.isAlive()) {
					thread.start();
				}
			}
		} else {
			log.error("PushPublishAll.startGetThread(): Not starting new threads. pollingThreadName size is already: "
					+ pollingThreadName.size());
		}
	}

	// custom
	private void getEdgeConnInfo(String edgeHost, IVHost vhost, WMSLogger log) {

		log.info("PushPublishAll.getEdgeConnInfo(): Thread ID initiallizing: "
				+ Thread.currentThread().getId());
		int connectionTotal = 0;
		IApplication application = vhost.getApplication(appName);
		String connectioninfoUrl = "http://" + edgeHost
				+ ":8086/connectioninfo";

		while (application.isRunning()) {
			log.debug("PushPublishAll.getEdgeConnInfo(): Thread ID: "
					+ Thread.currentThread().getId());

			byte[] result = HTTPUtils.HTTPRequestToByteArray(connectioninfoUrl,
					"GET", null, null);
			if (result != null) {
				String resultString = new String(result);
				connectionTotal = Integer.parseInt(resultString.split("=")[1]);

				if (connectionTotal >= removeThreshold1
						&& connectionTotal < removeThreshold2) {
					log.info("PushPublishAll.getEdgeConnInfo(): connectionTotal:"
							+ connectionTotal
							+ " greater than pushRemoveThreshold1:"
							+ removeThreshold1 + " edgeHost:" + edgeHost);
					String streamListThreshold = streamNameThreshold1;
					getPushPubStreams(application, streamListThreshold,
							edgeHost, log);
					try {
						Thread.sleep(checkInt);
					} catch (InterruptedException e) {
						log.error("PushPublishAll.getEdgeConnInfo(): Thread.sleep Exception:"
								+ e.toString() + " edgeHost:" + edgeHost);
					}
				} else if (connectionTotal > removeThreshold1
						&& connectionTotal >= removeThreshold2) {
					log.info("PushPublishAll.getEdgeConnInfo(): connectionTotal:"
							+ connectionTotal
							+ " greater than pushRemoveThreshold2:"
							+ removeThreshold2 + " edgeHost:" + edgeHost);
					String streamListThreshold = streamNameThreshold2;
					getPushPubStreams(application, streamListThreshold,
							edgeHost, log);
					try {
						Thread.sleep(checkInt);
					} catch (InterruptedException e) {
						log.error("PushPublishAll.getEdgeConnInfo(): Thread.sleep Exception:"
								+ e.toString() + " edgeHost:" + edgeHost);
					}
				} else {
					log.info("PushPublishAll.getEdgeConnInfo(): connectionTotal:"
							+ connectionTotal + " edgeHost:" + edgeHost);

					try {
						Thread.sleep(checkInt);
					} catch (InterruptedException e) {
						log.error("PushPublishAll.getEdgeConnInfo(): Thread.sleep Exception:"
								+ e.toString() + "  edgeHost:" + edgeHost);
					}
				}
			} else {
				log.warn("PushPublishAll.getEdgeConnInfo(): failed to get connectioninfo from edgeHost:"
						+ edgeHost + " retry in:" + checkInt + "ms");
				try {
					Thread.sleep(checkInt);
				} catch (InterruptedException e) {
					log.error("PushPublishAll.getEdgeConnInfo(): Thread.sleep Exception:"
							+ e.toString() + " edgeHost:" + edgeHost);
				}
			}
		}

		Thread.currentThread().interrupt();
		log.info("PushPublishAll.getEdgeConnInfo(): connectionTotal:"
				+ connectionTotal + " interrupt Thread ID: "
				+ Thread.currentThread().getId() + " for server:" + edgeHost);
		pollingThreadName.remove(edgeHost);
	}

	// custom
	private void getPushPubStreams(IApplication application,
			String streamListThreshold, String edgeHost, WMSLogger log) {

		if (application != null) {
			List<String> appInstances = application.getAppInstanceNames();
			if (appInstances.size() > 0) {
				Iterator<String> iterAppInstances = appInstances.iterator();
				while (iterAppInstances.hasNext()) {
					String appInstanceName = iterAppInstances.next();
					IApplicationInstance appInstance = application
							.getAppInstance(appInstanceName);
					List<String> publishedStreams = appInstance
							.getPublishStreamNames();
					Iterator<String> iter = publishedStreams.iterator();
					log.debug("PushPublishAll.getPublishedStreams(): publishedStreams:"
							+ publishedStreams.toString()
							+ " ThreadID:"
							+ Thread.currentThread().getId()
							+ " edgeHost:"
							+ edgeHost);
					while (iter.hasNext()) {
						String streamName = iter.next();
						log.debug("PushPublishAll.getPublishedStreams(): streamName:"
								+ streamName
								+ " ThreadID:"
								+ Thread.currentThread().getId()
								+ " edgeHost:"
								+ edgeHost);
						IMediaStream stream = appInstance.getStreams()
								.getStream(streamName);
						Pattern streamPattern = Pattern.compile(streamSource
								+ "(" + streamListThreshold + ")");
						Matcher streamMatch = streamPattern.matcher(streamName);
						if (streamMatch.find()) {
							log.debug("PushPublishAll.getPublishedStreams(): streamName:"
									+ streamName
									+ " MATCHED:"
									+ streamSource
									+ "("
									+ streamListThreshold
									+ ")"
									+ " ThreadID:"
									+ Thread.currentThread().getId()
									+ " edgeHost:" + edgeHost);
							boolean thresholdMet = true;
							stopPublisher(stream, streamName, thresholdMet,
									edgeHost, log);
						}
					}
				}
			} else {
				log.error("PushPublishAll.getPublishedStreams(): appInstances.size()"
						+ appInstances.size()
						+ " ThreadID:"
						+ Thread.currentThread().getId()
						+ " edgeHost:"
						+ edgeHost);
			}
		} else {
			log.error("PushPublishAll.getPublishedStreams(): vhost is null"
					+ " ThreadID:" + Thread.currentThread().getId()
					+ " edgeHost:" + edgeHost);
		}
	}
	// custom
}
// end

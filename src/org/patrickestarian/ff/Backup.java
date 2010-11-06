/**
 * Copyright (C) 2009 Patrick Estarian
 * 
 * FriendFeed Backup - downloads the feeds and any thumbnail, image, or
 * other files attached to the feed to your local file system.
 * 
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */


package org.patrickestarian.ff;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.patrickestarian.ff.schema.Entry;
import org.patrickestarian.ff.schema.Feed;
import org.patrickestarian.ff.schema.Thumbnail;


public class Backup {
	private static String ROOT = "/temp/logs/backup";
	private static String USER = "patrickestarian";
	private static String PROXY = "socks-proxy:8080";
	private static HashSet<String> DownloadedFiles = new HashSet<String>();

	public Backup() {
		try {
			//Collection<Feed> feeds = loadFeedsFromFF(USER);
			//Collection<Feed> feeds = loadFeedsFromXML(USER);
			//loadAllAttachments(feeds);
			download("http://friendfeed-media.s3.amazonaws.com/61774c3f7c55bdc01406dbdc34dc8588f883f528", "C:/backup/ff/patrickestarian/test.mp3");
		} catch (Exception e) {
			System.out.println("\n");
			System.err.println("ERROR: Could not perform the request. The root cause is: " + e);
			//e.printStackTrace();
		}
	}

	public Collection<Feed> loadFeedsFromFF(String userID) throws Exception {
		String userHome = ROOT + "/" + userID;
		File userHomeDir = new File(userHome);
		userHomeDir.mkdirs();
		
		Collection<Feed> feeds = new ArrayList<Feed>();
		int start = 0;
		while (true) {
			String from = "http://friendfeed-api.com/v2/feed/" + userID + "?format=xml&maxcomments=10000&maxlikes=10000&num=100&start=" + start;
			String to = getFileName(userID, start);
			
			String filePath = download(from, to);
			
			Feed feed = loadXML(filePath);
			if (feed == null) {
				break;
			}
			Collection<Entry> entries = feed.getEntry();
			if (entries == null  ||  entries.size() ==0) {
				File emptyFeed = new File(filePath);
				emptyFeed.delete();
				break;
			}
			feeds.add(feed);
			start += 100;
		}
		return feeds;
	}

	public Collection<Feed> loadFeedsFromXML(String userID) throws Exception {
		Collection<Feed> feeds = new ArrayList<Feed>();
		int start = 0;
		while (true) {
			String filePath = getFileName(userID, start) + ".xml";
			Feed feed = loadXML(filePath);
			if (feed == null) {
				break;
			}
			Collection<Entry> entries = feed.getEntry();
			if (entries == null  ||  entries.size() ==0) {
				break;
			}
			feeds.add(feed);
			start += 100;
		}
		
		return feeds;
	}

	public Feed loadXML(String xmlFilePath) throws Exception {
		File file = new File(xmlFilePath);
		if (!file.exists()) {
			return null;
		}

		JAXBContext jc = JAXBContext.newInstance("org.patrickestarian.ff.schema");
		Unmarshaller u = jc.createUnmarshaller();
		Feed feed = (Feed) u.unmarshal(file);
		
		return feed;
	}

	public void loadAllAttachments(Collection<Feed> feeds) throws Exception {
		for (Feed feed : feeds) {
			loadAttachmentsForFeed(feed);
		}
	}
	
	public void loadAttachmentsForFeed(Feed feed) throws Exception {
		String userID = feed.getId();
		
		Collection<Entry> entries = feed.getEntry();
		for (Entry entry : entries) {
			String feedID = entry.getId();
			String feedDir = ROOT + "/" + userID + "/" + feedID;
			
			Collection<Thumbnail> thumbnails = entry.getThumbnail();
			for (Thumbnail thumbnail : thumbnails) {
				String thumbnailLink = thumbnail.getLink();
				if (thumbnailLink.indexOf("friendfeed-media") > 0) {
					String linkFilePath = feedDir + "/" + thumbnailLink.substring(thumbnailLink.lastIndexOf("/"));
					download(thumbnailLink, linkFilePath);
				}

				String thumbnailURL = thumbnail.getUrl();
				if (thumbnailURL.indexOf("friendfeed-media") > 0) {
					String urlFilePath = feedDir + "/" + thumbnailURL.substring(thumbnailURL.lastIndexOf("/"));
					download(thumbnailURL, urlFilePath);
				}
			}
			
			Collection<org.patrickestarian.ff.schema.File> files = entry.getFile();
			for (org.patrickestarian.ff.schema.File file : files) {
				String from = file.getUrl();
				String to = feedDir + "/" + file.getName();
				download(from, to);
			}
		}
	}
	
	public String download(String from, String to) throws Exception {
		URL url = new URL(from);

		HttpURLConnection uc;
		if (PROXY == null) {
			uc = (HttpURLConnection) url.openConnection();
		} else {
			String server = null;
			int iport = -1;
			try {
				int idx = PROXY.indexOf(":");
				server = PROXY.substring(0, idx);
				String sport = PROXY.substring(idx+1);
				iport = Integer.valueOf(sport);
			} catch (Exception e) {
			}
			if (iport < 0) {
				throw new Exception("Invalid proxy");
			}
			SocketAddress socketAddress = new InetSocketAddress(server, iport);
			Proxy proxy = new Proxy(Proxy.Type.HTTP, socketAddress);
			uc = (HttpURLConnection) url.openConnection(proxy);
		}
		
		String contentType = uc.getContentType();
		
		String filePath = "";
		if (contentType.startsWith("application/xml")) {
			filePath = to + ".xml";
		} else if (contentType.startsWith("image/")) {
			filePath = to + "." + contentType.substring("image/".length());
		} else {
			System.out.println("Unknow content type: " + contentType);
		}
		
		int contentLength = uc.getContentLength();
		
		Map<String, List<String>> headers = uc.getHeaderFields();
		List<String> subHeaders = null;
		for (String headerName : headers.keySet()) {
			if (headerName != null  &&  headerName.equalsIgnoreCase("Content-Disposition")) {
				subHeaders = headers.get(headerName);
				break;
			}
		}
		
		if (subHeaders != null) {
			for (String subHeader : subHeaders) {
				int idx = subHeader.indexOf("filename");
				if (idx >= 0) {
					int s = subHeader.indexOf("\"")+1;
					int e = subHeader.indexOf("\"", s);
					filePath = subHeader.substring(s, e);
					filePath = to.substring(0, to.lastIndexOf('/')) + filePath;
					break;
				}
			}
		}
		
		String fileName = filePath.substring(filePath.lastIndexOf('/')+1);
		System.out.print("Downloading " + fileName + " (" + contentLength + " bytes) ... ");

		if (DownloadedFiles.contains(filePath)) {
			System.out.println("skipped.");
			return filePath;
		}

		InputStream raw = uc.getInputStream();
		InputStream in = new BufferedInputStream(raw);
		byte[] data = new byte[contentLength];
		int bytesRead = 0;
		int offset = 0;
		while (offset < contentLength) {
			bytesRead = in.read(data, offset, data.length - offset);
			if (bytesRead == -1)
				break;
			offset += bytesRead;
		}
		in.close();
		
		if (offset != contentLength) {
			throw new IOException("Only read " + offset + " bytes; Expected " + contentLength + " bytes");
		}

		System.out.println("\t to: " + filePath);

		String dir = filePath.substring(0, filePath.lastIndexOf('/'));
		File dirFile = new File(dir);
		if (!dirFile.exists()) {
			dirFile.mkdirs();
		}

		FileOutputStream out = new FileOutputStream(filePath);
		out.write(data);
		out.flush();
		out.close();
		
		DownloadedFiles.add(filePath);
		
		return filePath;
	}

	public String getFileName(String userID, int start) {
		String sstart = String.valueOf(start);
		while (sstart.length() < 4) {
			sstart = "0" + sstart;
		}

		String send = String.valueOf(start + 99);
		while (send.length() < 4) {
			send = "0" + send;
		}

		String fileName = ROOT + "/" + userID + "/" + userID + "-" + sstart + "-" + send;
		
		return fileName;
	}
	
	public static void main(String[] args) {
//		if (args.length < 2  ||  args.length > 3) {
//			System.out.println();
//			System.out.println("Format:  java -jar ffbackup.jar <user> <path> [proxy-server:port]");
//			System.out.println();
//			System.out.println("Example 1: java -jar ffbackup.jar patrickestarian c:/backup/ff");
//			System.out.println();
//			System.out.println("Example 2: java -jar ffbackup.jar patrickestarian c:/backup/ff proxy-server:8080");
//			System.out.println();
//			return;
//		}
//		
//		USER = args[0];
//		ROOT = args[1].replace('\\', '/');
//		
//		if (args.length == 3) {
//			PROXY = args[2];
//		}
		
		new Backup();
	}

}

package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class TxtSubscribe {

    /**
     * 判断是否为 M3U/M3U8 格式
     */
    private static boolean isM3UFormat(String str) {
        if (str == null) return false;
        String trimmed = str.trim();
        return trimmed.startsWith("#EXTM3U") || trimmed.startsWith("#extm3u");
    }

    /**
     * 解析直播源，自动识别 TVBox TXT 格式 和 M3U/M3U8 格式
     */
    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        if (str == null || str.trim().isEmpty()) return;
        if (isM3UFormat(str)) {
            parseM3U(linkedHashMap, str);
        } else {
            parseTxt(linkedHashMap, str);
        }
    }

    /**
     * 解析 TVBox 原有 TXT 格式（#genre# 分组）
     */
    private static void parseTxt(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> arrayList;
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            String readLine = bufferedReader.readLine();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap3 = linkedHashMap2;
            while (readLine != null) {
                if (readLine.trim().isEmpty()) {
                    readLine = bufferedReader.readLine();
                } else {
                    String[] split = readLine.split(",");
                    if (split.length < 2) {
                        readLine = bufferedReader.readLine();
                    } else {
                        if (readLine.contains("#genre#")) {
                            String trim = split[0].trim();
                            if (!linkedHashMap.containsKey(trim)) {
                                linkedHashMap3 = new LinkedHashMap<>();
                                linkedHashMap.put(trim, linkedHashMap3);
                            } else {
                                linkedHashMap3 = linkedHashMap.get(trim);
                            }
                        } else {
                            String trim2 = split[0].trim();
                            for (String str2 : split[1].trim().split("#")) {
                                String trim3 = str2.trim();
                                if (!trim3.isEmpty() && isValidUrl(trim3)) {
                                    if (!linkedHashMap3.containsKey(trim2)) {
                                        arrayList = new ArrayList<>();
                                        linkedHashMap3.put(trim2, arrayList);
                                    } else {
                                        arrayList = linkedHashMap3.get(trim2);
                                    }
                                    if (!arrayList.contains(trim3)) {
                                        arrayList.add(trim3);
                                    }
                                }
                            }
                        }
                        readLine = bufferedReader.readLine();
                    }
                }
            }
            bufferedReader.close();
            if (linkedHashMap2.isEmpty()) {
                return;
            }
            linkedHashMap.put("未分组", linkedHashMap2);
        } catch (Throwable unused) {
        }
    }

    /**
     * 解析标准 M3U/M3U8 格式
     * 支持：
     *   #EXTINF:-1 group-title="分组",频道名
     *   #EXTINF:-1 tvg-name="频道名" group-title="分组",频道名
     *   URL（下一行）
     */
    private static void parseM3U(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            String readLine = bufferedReader.readLine(); // 跳过 #EXTM3U 头部行（但先检查全局 x-tvg-url 等属性）
            String pendingChannelName = null;
            String pendingGroup = "未分组";

            while ((readLine = bufferedReader.readLine()) != null) {
                readLine = readLine.trim();
                if (readLine.isEmpty()) continue;

                if (readLine.toUpperCase().startsWith("#EXTINF")) {
                    // 解析频道名和分组
                    pendingGroup = extractM3UAttr(readLine, "group-title");
                    if (pendingGroup == null || pendingGroup.isEmpty()) {
                        pendingGroup = "未分组";
                    }
                    // 频道名在最后一个逗号之后
                    int commaIdx = readLine.lastIndexOf(",");
                    if (commaIdx >= 0 && commaIdx < readLine.length() - 1) {
                        pendingChannelName = readLine.substring(commaIdx + 1).trim();
                    } else {
                        pendingChannelName = null;
                    }
                } else if (!readLine.startsWith("#")) {
                    // 这是 URL 行
                    String url = readLine.trim();
                    if (isValidUrl(url) && pendingChannelName != null && !pendingChannelName.isEmpty()) {
                        LinkedHashMap<String, ArrayList<String>> groupMap;
                        if (!linkedHashMap.containsKey(pendingGroup)) {
                            groupMap = new LinkedHashMap<>();
                            linkedHashMap.put(pendingGroup, groupMap);
                        } else {
                            groupMap = linkedHashMap.get(pendingGroup);
                        }
                        if (!groupMap.containsKey(pendingChannelName)) {
                            groupMap.put(pendingChannelName, new ArrayList<String>());
                        }
                        ArrayList<String> urls = groupMap.get(pendingChannelName);
                        if (!urls.contains(url)) {
                            urls.add(url);
                        }
                    }
                    // 重置
                    pendingChannelName = null;
                    pendingGroup = "未分组";
                }
            }
            bufferedReader.close();
        } catch (Throwable unused) {
        }
    }

    /**
     * 从 #EXTINF 行中提取属性值，例如 group-title="xxx" 返回 xxx
     */
    private static String extractM3UAttr(String line, String attr) {
        String searchKey = attr + "=\"";
        int start = line.indexOf(searchKey);
        if (start < 0) {
            // 兼容单引号
            searchKey = attr + "='";
            start = line.indexOf(searchKey);
            if (start < 0) return null;
            start += searchKey.length();
            int end = line.indexOf("'", start);
            if (end < 0) return null;
            return line.substring(start, end).trim();
        }
        start += searchKey.length();
        int end = line.indexOf("\"", start);
        if (end < 0) return null;
        return line.substring(start, end).trim();
    }

    /**
     * 判断是否为合法的流媒体 URL
     */
    private static boolean isValidUrl(String url) {
        return url.startsWith("http://")
                || url.startsWith("https://")
                || url.startsWith("rtp://")
                || url.startsWith("rtsp://")
                || url.startsWith("rtmp://")
                || url.startsWith("rtmps://")
                || url.startsWith("p3p://")
                || url.startsWith("p2p://");
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray jsonarr = new JsonArray();
        for (String str : linkedHashMap.keySet()) {
            JsonArray jsonarr2 = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = linkedHashMap.get(str);
            if (!linkedHashMap2.isEmpty()) {
                for (String str2 : linkedHashMap2.keySet()) {
                    ArrayList<String> arrayList = linkedHashMap2.get(str2);
                    if (!arrayList.isEmpty()) {
                        JsonArray jsonarr3 = new JsonArray();
                        for (int i = 0; i < arrayList.size(); i++) {
                            jsonarr3.add(arrayList.get(i));
                        }
                        JsonObject jsonobj = new JsonObject();
                        try {
                            jsonobj.addProperty("name", str2);
                            jsonobj.add("urls", jsonarr3);
                        } catch (Throwable e) {
                        }
                        jsonarr2.add(jsonobj);
                    }
                }
                JsonObject jsonobj2 = new JsonObject();
                try {
                    jsonobj2.addProperty("group", str);
                    jsonobj2.add("channels", jsonarr2);
                } catch (Throwable e) {
                }
                jsonarr.add(jsonobj2);
            }
        }
        return jsonarr;
    }
}

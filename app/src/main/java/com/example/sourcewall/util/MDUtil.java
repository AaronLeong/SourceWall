package com.example.sourcewall.util;

import com.example.sourcewall.connection.ResultObject;
import com.example.sourcewall.connection.api.APIBase;

/**
 * Created by NashLegend on 2014/12/5 0005
 */
public class MDUtil {

    /**
     * 通过github接口转换markdown，一小时只能60次
     *
     * @param text
     * @return
     */
    public static ResultObject parseMarkdownByGitHub(String text) {
        return APIBase.parseMarkdownByGitHub(text);
    }

    /**
     * Markdown转换为Html
     *
     * @param text
     * @return
     */
    public static String Markdown2HtmlDumb(String text) {
        StringBuilder sb = new StringBuilder();
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            sb.append("<p>").append(markdown2HtmlImage(paragraph)).append("</p>");
        }
        return sb.toString();
    }

    /**
     * Markdown转换为Text
     *
     * @param text
     * @return
     */
    public static String Markdown2TextDumb(String text) {
        StringBuilder sb = new StringBuilder();
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            sb.append("<p>").append(markdown2TextImage(paragraph)).append("</p>");
        }
        return sb.toString();
    }

    /**
     * Text格式轮换为Html
     *
     * @param text
     * @return
     */
    public static String Text2HtmlDumb(String text) {
        StringBuilder sb = new StringBuilder();
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            sb.append("<p>").append(markdown2TextImage(paragraph)).append("</p>");
        }
        return sb.toString();
    }

    /**
     * Markdown 图片转换为Html格式
     *
     * @param image
     * @return
     */
    private static String markdown2HtmlImage(String image) {
        return image.replaceAll("\\!\\[[^\\]]*?\\]\\((.*?)\\)", "<img src=\"" + "$1" + "\" style=\"max-width:100%;\">");
    }

    /**
     * Markdown图片转换为文本描述格式 [image]http://xxx[/image]
     *
     * @param image
     * @return
     */
    private static String markdown2TextImage(String image) {
        return image.replaceAll("\\!\\[[^\\]]*?\\]\\((.*?)\\)", "[image]" + "$1" + "[/image]");
    }

    /**
     * 文本描述格式转换为Html格式 [image]http://xxx[/image]
     *
     * @param image
     * @return
     */
    public static String text2HtmlImage(String image) {
        return image.replaceAll("\\[image\\](.*?)\\[/image\\]", "<img src=\"" + "$1" + "\" style=\"max-width:100%;\">");
    }

    /**
     * 文本描述格式转换为Markdown图片格式 [image]http://xxx[/image]
     *
     * @param image
     * @return
     */
    public static String text2MarkdownImage(String image) {
        return image.replaceAll("\\[image\\](.*?)\\[/image\\]", "![](" + "$1" + ")");
    }
}

package org.brightstats.report;
/** Safe XML/HTML text. Source records remain unchanged; invalid XML code points render as U+FFFD. */
public final class XmlText {
 private XmlText(){}
 public static String escape(String value){if(value==null)return "";StringBuilder clean=new StringBuilder();value.codePoints().forEach(code->{if(code<32&&code!=9&&code!=10&&code!=13||code==0xfffe||code==0xffff||code>=0xd800&&code<=0xdfff)clean.appendCodePoint(0xfffd);else clean.appendCodePoint(code);});return clean.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}

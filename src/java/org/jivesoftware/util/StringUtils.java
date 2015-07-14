/**
 * $Revision: 3870 $
 * $Date: 2006-05-10 15:48:11 -0300 (Wed, 10 May 2006) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.jivesoftware.stringprep.IDNA;
import org.jivesoftware.stringprep.Stringprep;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.BreakIterator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to peform common String manipulation algorithms.
 */
public class StringUtils {

    // Constants used by escapeHTMLTags
    private static final char[] QUOTE_ENCODE = "&quot;".toCharArray();
    private static final char[] AMP_ENCODE = "&amp;".toCharArray();
    private static final char[] LT_ENCODE = "&lt;".toCharArray();
    private static final char[] GT_ENCODE = "&gt;".toCharArray();

    // Stringprep operations are very expensive. Therefore, we cache node, domain and
    // resource values that have already had stringprep applied so that we can check
    // incoming values against the cache.
    private static Map stringprepCache = Collections.synchronizedMap(new Cache(10000));

    private StringUtils() {
        // Not instantiable.
    }

    /**
     * Returns the name portion of a XMPP address. For example, for the
     * address "matt@jivesoftware.com/Smack", "matt" would be returned. If no
     * username is present in the address, the empty string will be returned.
     *
     * @param XMPPAddress the XMPP address.
     * @return the name portion of the XMPP address.
     */
    public static String parseName(String XMPPAddress) {
        if (XMPPAddress == null) {
            return null;
        }
        int atIndex = XMPPAddress.indexOf("@");
        if (atIndex <= 0) {
            return "";
        }
        else {
            return XMPPAddress.substring(0, atIndex);
        }
    }

    /**
     * Returns the server portion of a XMPP address. For example, for the
     * address "matt@jivesoftware.com/Smack", "jivesoftware.com" would be returned.
     * If no server is present in the address, the empty string will be returned.
     *
     * @param XMPPAddress the XMPP address.
     * @return the server portion of the XMPP address.
     */
    public static String parseServer(String XMPPAddress) {
        if (XMPPAddress == null) {
            return null;
        }
        int atIndex = XMPPAddress.indexOf("@");
        // If the String ends with '@', return the empty string.
        if (atIndex + 1 > XMPPAddress.length()) {
            return "";
        }
        int slashIndex = XMPPAddress.indexOf("/");
        if (slashIndex > 0) {
            return XMPPAddress.substring(atIndex + 1, slashIndex);
        }
        else {
            return XMPPAddress.substring(atIndex + 1);
        }
    }

    /**
     * Returns the resource portion of a XMPP address. For example, for the
     * address "matt@jivesoftware.com/Smack", "Smack" would be returned. If no
     * resource is present in the address, the empty string will be returned.
     *
     * @param XMPPAddress the XMPP address.
     * @return the resource portion of the XMPP address.
     */
    public static String parseResource(String XMPPAddress) {
        if (XMPPAddress == null) {
            return null;
        }
        int slashIndex = XMPPAddress.indexOf("/");
        if (slashIndex + 1 > XMPPAddress.length() || slashIndex < 0) {
            return "";
        }
        else {
            return XMPPAddress.substring(slashIndex + 1);
        }
    }

    /**
     * Returns the XMPP address with any resource information removed. For example,
     * for the address "matt@jivesoftware.com/Smack", "matt@jivesoftware.com" would
     * be returned.
     *
     * @param XMPPAddress the XMPP address.
     * @return the bare XMPP address without resource information.
     */
    public static String parseBareAddress(String XMPPAddress) {
        if (XMPPAddress == null) {
            return null;
        }
        int slashIndex = XMPPAddress.indexOf("/");
        if (slashIndex < 0) {
            return XMPPAddress;
        }
        else if (slashIndex == 0) {
            return "";
        }
        else {
            return XMPPAddress.substring(0, slashIndex);
        }
    }

    /**
     * Replaces all instances of oldString with newString in string.
     *
     * @param string the String to search to perform replacements on.
     * @param oldString the String that should be replaced by newString.
     * @param newString the String that will replace all instances of oldString.
     * @return a String will all instances of oldString replaced by newString.
     */
    public static String replace(String string, String oldString, String newString) {
        if (string == null) {
            return null;
        }
        int i = 0;
        // Make sure that oldString appears at least once before doing any processing.
        if ((i = string.indexOf(oldString, i)) >= 0) {
            // Use char []'s, as they are more efficient to deal with.
            char[] string2 = string.toCharArray();
            char[] newString2 = newString.toCharArray();
            int oLength = oldString.length();
            StringBuilder buf = new StringBuilder(string2.length);
            buf.append(string2, 0, i).append(newString2);
            i += oLength;
            int j = i;
            // Replace all remaining instances of oldString with newString.
            while ((i = string.indexOf(oldString, i)) > 0) {
                buf.append(string2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }
            buf.append(string2, j, string2.length - j);
            return buf.toString();
        }
        return string;
    }

    /**
     * Replaces all instances of oldString with newString in line with the
     * added feature that matches of newString in oldString ignore case.
     *
     * @param line      the String to search to perform replacements on
     * @param oldString the String that should be replaced by newString
     * @param newString the String that will replace all instances of oldString
     * @return a String will all instances of oldString replaced by newString
     */
    public static String replaceIgnoreCase(String line, String oldString,
                                                 String newString) {
        if (line == null) {
            return null;
        }
        String lcLine = line.toLowerCase();
        String lcOldString = oldString.toLowerCase();
        int i = 0;
        if ((i = lcLine.indexOf(lcOldString, i)) >= 0) {
            char[] line2 = line.toCharArray();
            char[] newString2 = newString.toCharArray();
            int oLength = oldString.length();
            StringBuilder buf = new StringBuilder(line2.length);
            buf.append(line2, 0, i).append(newString2);
            i += oLength;
            int j = i;
            while ((i = lcLine.indexOf(lcOldString, i)) > 0) {
                buf.append(line2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }
            buf.append(line2, j, line2.length - j);
            return buf.toString();
        }
        return line;
    }

    /**
     * Replaces all instances of oldString with newString in line with the
     * added feature that matches of newString in oldString ignore case.
     * The count paramater is set to the number of replaces performed.
     *
     * @param line      the String to search to perform replacements on
     * @param oldString the String that should be replaced by newString
     * @param newString the String that will replace all instances of oldString
     * @param count     a value that will be updated with the number of replaces
     *                  performed.
     * @return a String will all instances of oldString replaced by newString
     */
    public static String replaceIgnoreCase(String line, String oldString,
            String newString, int[] count)
    {
        if (line == null) {
            return null;
        }
        String lcLine = line.toLowerCase();
        String lcOldString = oldString.toLowerCase();
        int i = 0;
        if ((i = lcLine.indexOf(lcOldString, i)) >= 0) {
            int counter = 1;
            char[] line2 = line.toCharArray();
            char[] newString2 = newString.toCharArray();
            int oLength = oldString.length();
            StringBuilder buf = new StringBuilder(line2.length);
            buf.append(line2, 0, i).append(newString2);
            i += oLength;
            int j = i;
            while ((i = lcLine.indexOf(lcOldString, i)) > 0) {
                counter++;
                buf.append(line2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }
            buf.append(line2, j, line2.length - j);
            count[0] = counter;
            return buf.toString();
        }
        return line;
    }

    /**
     * Replaces all instances of oldString with newString in line.
     * The count Integer is updated with number of replaces.
     *
     * @param line the String to search to perform replacements on.
     * @param oldString the String that should be replaced by newString.
     * @param newString the String that will replace all instances of oldString.
     * @return a String will all instances of oldString replaced by newString.
     */
    public static String replace(String line, String oldString,
            String newString, int[] count)
    {
        if (line == null) {
            return null;
        }
        int i = 0;
        if ((i = line.indexOf(oldString, i)) >= 0) {
            int counter = 1;
            char[] line2 = line.toCharArray();
            char[] newString2 = newString.toCharArray();
            int oLength = oldString.length();
            StringBuilder buf = new StringBuilder(line2.length);
            buf.append(line2, 0, i).append(newString2);
            i += oLength;
            int j = i;
            while ((i = line.indexOf(oldString, i)) > 0) {
                counter++;
                buf.append(line2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }
            buf.append(line2, j, line2.length - j);
            count[0] = counter;
            return buf.toString();
        }
        return line;
    }

    /**
     * This method takes a string and strips out all tags except <br> tags while still leaving
     * the tag body intact.
     *
     * @param in the text to be converted.
     * @return the input string with all tags removed.
     */
    public static String stripTags(String in) {
        if (in == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = in.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int)(len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            }
            else if (ch == '<') {
                if (i + 3 < len && input[i + 1] == 'b' && input[i + 2] == 'r' && input[i + 3] == '>') {
                    i += 3;
                    continue;
                }
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
            }
            else if (ch == '>') {
                last = i + 1;
            }
        }
        if (last == 0) {
            return in;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * This method takes a string which may contain HTML tags (ie, &lt;b&gt;,
     * &lt;table&gt;, etc) and converts the '&lt'' and '&gt;' characters to
     * their HTML escape sequences.
     *
     * @param in the text to be converted.
     * @return the input string with the characters '&lt;' and '&gt;' replaced
     *         with their HTML escape sequences.
     */
    public static String escapeHTMLTags(String in) {
        if (in == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = in.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int)(len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            }
            else if (ch == '<') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(LT_ENCODE);
            }
            else if (ch == '>') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(GT_ENCODE);
            }
        }
        if (last == 0) {
            return in;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * Used by the hash method.
     */
    private static Map<String, MessageDigest> digests =
            new ConcurrentHashMap<String, MessageDigest>();

    /**
     * Hashes a String using the Md5 algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes
     * a bottleneck in your code, you may wish to maintain a pool of
     * MessageDigest objects instead of using this method.
     * <p/>
     * A hash is a one-way function -- that is, given an
     * input, an output is easily computed. However, given the output, the
     * input is almost impossible to compute. This is useful for passwords
     * since we can store the hash and a hacker will then have a very hard time
     * determining the original password.
     * <p/>
     * In Jive, every time a user logs in, we simply
     * take their plain text password, compute the hash, and compare the
     * generated hash to the stored hash. Since it is almost impossible that
     * two passwords will generate the same hash, we know if the user gave us
     * the correct password or not. The only negative to this system is that
     * password recovery is basically impossible. Therefore, a reset password
     * method is used instead.
     *
     * @param data the String to compute the hash of.
     * @return a hashed version of the passed-in String
     */
    public static String hash(String data) {
        return hash(data, "MD5");
    }

    /**
     * Hashes a String using the specified algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes
     * a bottleneck in your code, you may wish to maintain a pool of
     * MessageDigest objects instead of using this method.
     * <p/>
     * A hash is a one-way function -- that is, given an
     * input, an output is easily computed. However, given the output, the
     * input is almost impossible to compute. This is useful for passwords
     * since we can store the hash and a hacker will then have a very hard time
     * determining the original password.
     * <p/>
     * In Jive, every time a user logs in, we simply
     * take their plain text password, compute the hash, and compare the
     * generated hash to the stored hash. Since it is almost impossible that
     * two passwords will generate the same hash, we know if the user gave us
     * the correct password or not. The only negative to this system is that
     * password recovery is basically impossible. Therefore, a reset password
     * method is used instead.
     *
     * @param data the String to compute the hash of.
     * @param algorithm the name of the algorithm requested.
     * @return a hashed version of the passed-in String
     */
    public static String hash(String data, String algorithm) {
        try {
            return hash(data.getBytes("utf-8"), algorithm);
        }
        catch (UnsupportedEncodingException e) {
            Log.error(e);
        }
        return data;
    }

    /**
     * Hashes a byte array using the specified algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes
     * a bottleneck in your code, you may wish to maintain a pool of
     * MessageDigest objects instead of using this method.
     * <p/>
     * A hash is a one-way function -- that is, given an
     * input, an output is easily computed. However, given the output, the
     * input is almost impossible to compute. This is useful for passwords
     * since we can store the hash and a hacker will then have a very hard time
     * determining the original password.
     * <p/>
     * In Jive, every time a user logs in, we simply
     * take their plain text password, compute the hash, and compare the
     * generated hash to the stored hash. Since it is almost impossible that
     * two passwords will generate the same hash, we know if the user gave us
     * the correct password or not. The only negative to this system is that
     * password recovery is basically impossible. Therefore, a reset password
     * method is used instead.
     *
     * @param bytes the byte array to compute the hash of.
     * @param algorithm the name of the algorithm requested.
     * @return a hashed version of the passed-in String
     */
    public static String hash(byte[] bytes, String algorithm) {
        synchronized (algorithm.intern()) {
            MessageDigest digest = digests.get(algorithm);
            if (digest == null) {
                try {
                    digest = MessageDigest.getInstance(algorithm);
                    digests.put(algorithm, digest);
                }
                catch (NoSuchAlgorithmException nsae) {
                    Log.error("Failed to load the " + algorithm + " MessageDigest. " +
                            "Jive will be unable to function normally.", nsae);
                    return null;
                }
            }
            // Now, compute hash.
            digest.update(bytes);
            return encodeHex(digest.digest());
        }
    }

    /**
     * Turns an array of bytes into a String representing each byte as an
     * unsigned hex number.
     * <p/>
     * Method by Santeri Paavolainen, Helsinki Finland 1996<br>
     * (c) Santeri Paavolainen, Helsinki Finland 1996<br>
     * Distributed under LGPL.
     *
     * @param bytes an array of bytes to convert to a hex-string
     * @return generated hex string
     */
    public static String encodeHex(byte[] bytes) {
        StringBuilder buf = new StringBuilder(bytes.length * 2);
        int i;

        for (i = 0; i < bytes.length; i++) {
            if (((int)bytes[i] & 0xff) < 0x10) {
                buf.append("0");
            }
            buf.append(Long.toString((int)bytes[i] & 0xff, 16));
        }
        return buf.toString();
    }

    /**
     * Turns a hex encoded string into a byte array. It is specifically meant
     * to "reverse" the toHex(byte[]) method.
     *
     * @param hex a hex encoded String to transform into a byte array.
     * @return a byte array representing the hex String[
     */
    public static byte[] decodeHex(String hex) {
        char[] chars = hex.toCharArray();
        byte[] bytes = new byte[chars.length / 2];
        int byteCount = 0;
        for (int i = 0; i < chars.length; i += 2) {
            int newByte = 0x00;
            newByte |= hexCharToByte(chars[i]);
            newByte <<= 4;
            newByte |= hexCharToByte(chars[i + 1]);
            bytes[byteCount] = (byte)newByte;
            byteCount++;
        }
        return bytes;
    }

    /**
     * Returns the the byte value of a hexadecmical char (0-f). It's assumed
     * that the hexidecimal chars are lower case as appropriate.
     *
     * @param ch a hexedicmal character (0-f)
     * @return the byte value of the character (0x00-0x0F)
     */
    private static byte hexCharToByte(char ch) {
        switch (ch) {
            case '0':
                return 0x00;
            case '1':
                return 0x01;
            case '2':
                return 0x02;
            case '3':
                return 0x03;
            case '4':
                return 0x04;
            case '5':
                return 0x05;
            case '6':
                return 0x06;
            case '7':
                return 0x07;
            case '8':
                return 0x08;
            case '9':
                return 0x09;
            case 'a':
                return 0x0A;
            case 'b':
                return 0x0B;
            case 'c':
                return 0x0C;
            case 'd':
                return 0x0D;
            case 'e':
                return 0x0E;
            case 'f':
                return 0x0F;
        }
        return 0x00;
    }

    /**
     * Encodes a String as a base64 String.
     *
     * @param data a String to encode.
     * @return a base64 encoded String.
     */
    public static String encodeBase64(String data) {
        byte[] bytes = null;
        try {
            bytes = data.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException uee) {
            Log.error(uee);
        }
        return encodeBase64(bytes);
    }

    /**
     * Encodes a byte array into a base64 String.
     *
     * @param data a byte array to encode.
     * @return a base64 encode String.
     */
    public static String encodeBase64(byte[] data) {
        // Encode the String. We pass in a flag to specify that line
        // breaks not be added. This is consistent with our previous base64
        // implementation. Section 2.1 of 3548 (base64 spec) also specifies
        // no line breaks by default.
        return Base64.encodeBytes(data, Base64.DONT_BREAK_LINES);
    }

    /**
     * Decodes a base64 String.
     *
     * @param data a base64 encoded String to decode.
     * @return the decoded String.
     */
    public static byte[] decodeBase64(String data) {
        return Base64.decode(data);
    }

    /**
     * Converts a line of text into an array of lower case words using a
     * BreakIterator.wordInstance().<p>
     *
     * This method is under the Jive Open Source Software License and was
     * written by Mark Imbriaco.
     *
     * @param text a String of text to convert into an array of words
     * @return text broken up into an array of words.
     */
    public static String[] toLowerCaseWordArray(String text) {
        if (text == null || text.length() == 0) {
            return new String[0];
        }

        List<String> wordList = new ArrayList<String>();
        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);
        int start = 0;

        for (int end = boundary.next(); end != BreakIterator.DONE;
             start = end, end = boundary.next()) {
            String tmp = text.substring(start, end).trim();
            // Remove characters that are not needed.
            tmp = replace(tmp, "+", "");
            tmp = replace(tmp, "/", "");
            tmp = replace(tmp, "\\", "");
            tmp = replace(tmp, "#", "");
            tmp = replace(tmp, "*", "");
            tmp = replace(tmp, ")", "");
            tmp = replace(tmp, "(", "");
            tmp = replace(tmp, "&", "");
            if (tmp.length() > 0) {
                wordList.add(tmp);
            }
        }
        return wordList.toArray(new String[wordList.size()]);
    }

    /**
     * Pseudo-random number generator object for use with randomString().
     * The Random class is not considered to be cryptographically secure, so
     * only use these random Strings for low to medium security applications.
     */
    private static Random randGen = new Random();

    /**
     * Array of numbers and letters of mixed case. Numbers appear in the list
     * twice so that there is a more equal chance that a number will be picked.
     * We can use the array to get a random number or letter by picking a random
     * array index.
     */
    private static char[] numbersAndLetters = ("0123456789abcdefghijklmnopqrstuvwxyz" +
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();

    /**
     * Returns a random String of numbers and letters (lower and upper case)
     * of the specified length. The method uses the Random class that is
     * built-in to Java which is suitable for low to medium grade security uses.
     * This means that the output is only pseudo random, i.e., each number is
     * mathematically generated so is not truly random.<p>
     * <p/>
     * The specified length must be at least one. If not, the method will return
     * null.
     *
     * @param length the desired length of the random String to return.
     * @return a random String of numbers and letters of the specified length.
     */
    public static String randomString(int length) {
        if (length < 1) {
            return null;
        }
        // Create a char buffer to put random letters and numbers in.
        char[] randBuffer = new char[length];
        for (int i = 0; i < randBuffer.length; i++) {
            randBuffer[i] = numbersAndLetters[randGen.nextInt(71)];
        }
        return new String(randBuffer);
    }

    /**
     * Intelligently chops a String at a word boundary (whitespace) that occurs
     * at the specified index in the argument or before. However, if there is a
     * newline character before <code>length</code>, the String will be chopped
     * there. If no newline or whitespace is found in <code>string</code> up to
     * the index <code>length</code>, the String will chopped at <code>length</code>.
     * <p/>
     * For example, chopAtWord("This is a nice String", 10) will return
     * "This is a" which is the first word boundary less than or equal to 10
     * characters into the original String.
     *
     * @param string the String to chop.
     * @param length the index in <code>string</code> to start looking for a
     *               whitespace boundary at.
     * @return a substring of <code>string</code> whose length is less than or
     *         equal to <code>length</code>, and that is chopped at whitespace.
     */
    public static String chopAtWord(String string, int length) {
        if (string == null || string.length() == 0) {
            return string;
        }

        char[] charArray = string.toCharArray();
        int sLength = string.length();
        if (length < sLength) {
            sLength = length;
        }

        // First check if there is a newline character before length; if so,
        // chop word there.
        for (int i = 0; i < sLength - 1; i++) {
            // Windows
            if (charArray[i] == '\r' && charArray[i + 1] == '\n') {
                return string.substring(0, i + 1);
            }
            // Unix
            else if (charArray[i] == '\n') {
                return string.substring(0, i);
            }
        }
        // Also check boundary case of Unix newline
        if (charArray[sLength - 1] == '\n') {
            return string.substring(0, sLength - 1);
        }

        // Done checking for newline, now see if the total string is less than
        // the specified chop point.
        if (string.length() < length) {
            return string;
        }

        // No newline, so chop at the first whitespace.
        for (int i = length - 1; i > 0; i--) {
            if (charArray[i] == ' ') {
                return string.substring(0, i).trim();
            }
        }

        // Did not find word boundary so return original String chopped at
        // specified length.
        return string.substring(0, length);
    }

    /**
     * Reformats a string where lines that are longer than <tt>width</tt>
     * are split apart at the earliest wordbreak or at maxLength, whichever is
     * sooner. If the width specified is less than 5 or greater than the input
     * Strings length the string will be returned as is.
     * <p/>
     * Please note that this method can be lossy - trailing spaces on wrapped
     * lines may be trimmed.
     *
     * @param input the String to reformat.
     * @param width the maximum length of any one line.
     * @return a new String with reformatted as needed.
     */
    public static String wordWrap(String input, int width, Locale locale) {
        // protect ourselves
        if (input == null) {
            return "";
        }
        else if (width < 5) {
            return input;
        }
        else if (width >= input.length()) {
            return input;
        }

        // default locale
        if (locale == null) {
            locale = JiveGlobals.getLocale();
        }

        StringBuilder buf = new StringBuilder(input);
        boolean endOfLine = false;
        int lineStart = 0;

        for (int i = 0; i < buf.length(); i++) {
            if (buf.charAt(i) == '\n') {
                lineStart = i + 1;
                endOfLine = true;
            }

            // handle splitting at width character
            if (i > lineStart + width - 1) {
                if (!endOfLine) {
                    int limit = i - lineStart - 1;
                    BreakIterator breaks = BreakIterator.getLineInstance(locale);
                    breaks.setText(buf.substring(lineStart, i));
                    int end = breaks.last();

                    // if the last character in the search string isn't a space,
                    // we can't split on it (looks bad). Search for a previous
                    // break character
                    if (end == limit + 1) {
                        if (!Character.isWhitespace(buf.charAt(lineStart + end))) {
                            end = breaks.preceding(end - 1);
                        }
                    }

                    // if the last character is a space, replace it with a \n
                    if (end != BreakIterator.DONE && end == limit + 1) {
                        buf.replace(lineStart + end, lineStart + end + 1, "\n");
                        lineStart = lineStart + end;
                    }
                    // otherwise, just insert a \n
                    else if (end != BreakIterator.DONE && end != 0) {
                        buf.insert(lineStart + end, '\n');
                        lineStart = lineStart + end + 1;
                    }
                    else {
                        buf.insert(i, '\n');
                        lineStart = i + 1;
                    }
                }
                else {
                    buf.insert(i, '\n');
                    lineStart = i + 1;
                    endOfLine = false;
                }
            }
        }

        return buf.toString();
    }

    /**
     * Escapes all necessary characters in the String so that it can be used in SQL
     *
     * @param string the string to escape.
     * @return the string with appropriate characters escaped.
     */
    public static String escapeForSQL(String string) {
        if (string == null) {
            return null;
        }
        else if (string.length() == 0) {
            return string;
        }

        char ch;
        char[] input = string.toCharArray();
        int i = 0;
        int last = 0;
        int len = input.length;
        StringBuilder out = null;
        for (; i < len; i++) {
            ch = input[i];

            if (ch == '\'') {
                if (out == null) {
                     out = new StringBuilder(len + 2);
                }
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append('\'').append('\'');
            }
        }

        if (out == null) {
            return string;
        }
        else if (i > last) {
            out.append(input, last, i - last);
        }

        return out.toString();
    }

    /**
     * Escapes all necessary characters in the String so that it can be used
     * in an XML doc.
     *
     * @param string the string to escape.
     * @return the string with appropriate characters escaped.
     */
    public static String escapeForXML(String string) {
        if (string == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = string.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int)(len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            }
            else if (ch == '<') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(LT_ENCODE);
            }
            else if (ch == '&') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(AMP_ENCODE);
            }
            else if (ch == '"') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(QUOTE_ENCODE);
            }
        }
        if (last == 0) {
            return string;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * Unescapes the String by converting XML escape sequences back into normal
     * characters.
     *
     * @param string the string to unescape.
     * @return the string with appropriate characters unescaped.
     */
    public static String unescapeFromXML(String string) {
        string = replace(string, "&lt;", "<");
        string = replace(string, "&gt;", ">");
        string = replace(string, "&quot;", "\"");
        return replace(string, "&amp;", "&");
    }

    private static final char[] zeroArray =
            "0000000000000000000000000000000000000000000000000000000000000000".toCharArray();

    /**
     * Pads the supplied String with 0's to the specified length and returns
     * the result as a new String. For example, if the initial String is
     * "9999" and the desired length is 8, the result would be "00009999".
     * This type of padding is useful for creating numerical values that need
     * to be stored and sorted as character data. Note: the current
     * implementation of this method allows for a maximum <tt>length</tt> of
     * 64.
     *
     * @param string the original String to pad.
     * @param length the desired length of the new padded String.
     * @return a new String padded with the required number of 0's.
     */
    public static String zeroPadString(String string, int length) {
        if (string == null || string.length() > length) {
            return string;
        }
        StringBuilder buf = new StringBuilder(length);
        buf.append(zeroArray, 0, length - string.length()).append(string);
        return buf.toString();
    }

    /**
     * Formats a Date as a fifteen character long String made up of the Date's
     * padded millisecond value.
     *
     * @return a Date encoded as a String.
     */
    public static String dateToMillis(Date date) {
        return zeroPadString(Long.toString(date.getTime()), 15);
    }

    /**
     * Validates that the provided JID address is well-formed. Note that doing stringprep
     * operations is very expensive. We are using a Cache to reduce number of stringprep
     * operations but some of them will eventually be needed.
     *
     * @param jid the JID address to validate.
     * @return true if the address is well-formed.
     */
    public static boolean validateJID(String jid) {
        String node = null , domain, resource;
        if (jid == null) {
            return true;
        }

        int atIndex = jid.indexOf("@");
        int slashIndex = jid.indexOf("/");

        // Node
        if (atIndex > 0) {
            node = jid.substring(0, atIndex);
        }

        // Domain
        if (atIndex + 1 > jid.length()) {
            throw new IllegalArgumentException("JID with empty domain not valid");
        }
        if (atIndex < 0) {
            if (slashIndex > 0) {
                domain = jid.substring(0, slashIndex);
            }
            else {
                domain = jid;
            }
        }
        else {
            if (slashIndex > 0) {
                domain = jid.substring(atIndex + 1, slashIndex);
            }
            else {
                domain = jid.substring(atIndex + 1);
            }
        }

        // Resource
        if (slashIndex + 1 > jid.length() || slashIndex < 0) {
            resource = null;
        }
        else {
            resource = jid.substring(slashIndex + 1);
        }


        // Set node and resource to null if they are the empty string.
        if (node != null && node.equals("")) {
            node = null;
        }
        if (resource != null && resource.equals("")) {
            resource = null;
        }
        // Stringprep (node prep, resourceprep, etc).
        try {
            if (!stringprepCache.containsKey(node)) {
                node = Stringprep.nodeprep(node);
                // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
                if (node != null && node.length()*2 > 1023) {
                    Log.warn("Node cannot be larger than 1023 bytes. " +
                            "Size is " + (node.length() * 2) + " bytes.");
                    return false;
                }
                stringprepCache.put(node, null);
            }
            // XMPP specifies that domains should be run through IDNA and
            // that they should be run through nameprep before doing any
            // comparisons. We always run the domain through nameprep to
            // make comparisons easier later.
            if (!stringprepCache.containsKey(domain)) {
                domain = Stringprep.nameprep(IDNA.toASCII(domain), false);
                // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
                if (domain.length()*2 > 1023) {
                    Log.warn("Domain cannot be larger than 1023 bytes. " +
                            "Size is " + (domain.length() * 2) + " bytes.");
                    return false;
                }
                stringprepCache.put(domain, null);
            }
            if (!stringprepCache.containsKey(resource)) {
                resource = Stringprep.resourceprep(resource);
                // Validate field is not greater than 1023 bytes. UTF-8 characters use two bytes.
                if (resource != null && resource.length()*2 > 1023) {
                    Log.warn("Resource cannot be larger than 1023 bytes. " +
                            "Size is " + (resource.length() * 2) + " bytes.");
                    return false;
                }
                stringprepCache.put(resource, null);
            }
        }
        catch (Exception e) {
            Log.warn("Failed to validate JID: " + jid, e);
            return false;
        }
        return true;
    }

    /**
     * A simple cache class that extends LinkedHashMap. It uses an LRU policy to
     * keep the cache at a maximum size.
     */
    private static class Cache extends LinkedHashMap {

        private int maxSize;

        public Cache(int maxSize) {
            super(64, .75f, true);
            this.maxSize = maxSize;
        }

        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > maxSize;
        }
    }
}
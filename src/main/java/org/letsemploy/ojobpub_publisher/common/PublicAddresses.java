package org.letsemploy.ojobpub_publisher.common;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * The fence around a request to an address a user may choose (spec 7.27,
 * 7.23): a provider's picture, an employer's website. {@code https} to public
 * addresses only, so the application cannot be made to probe its own network.
 */
public final class PublicAddresses {

    private PublicAddresses() {
    }

    /**
     * {@code https}, to a host whose every address is public. Checked by
     * resolving the name first; a name that resolves elsewhere a moment later can
     * still slip past; each caller limits what it does with the answer.
     */
    public static boolean isPublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        // Only the https port. Any other would make the fetch a probe of
        // that port on a public host, answered by whether the connection opens.
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    return false;
                }
            }
            return addresses.length > 0;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            int third = b[2] & 0xff;
            return first != 0                                         // "this" network
                    && !(first == 100 && second >= 64 && second < 128) // carrier-grade NAT
                    && !(first == 192 && second == 0 && third == 0)   // IETF protocol assignments
                    && !(first == 192 && second == 0 && third == 2)   // documentation
                    && !(first == 198 && second == 51 && third == 100) // documentation
                    && !(first == 203 && second == 0 && third == 113) // documentation
                    && !(first == 192 && second == 88 && third == 99) // 6to4 relays, deprecated
                    && !(first == 198 && (second == 18 || second == 19))  // benchmarking
                    && first < 240;                                    // reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(b);
        }
        return false;
    }

    /**
     * Allowed, not refused: only global unicast {@code 2000::/3} is public, less
     * the special ranges inside it. A refusal list missed every form that carries
     * an IPv4 address, and those reach whatever that address is.
     */
    private static boolean isPublicIpv6(byte[] b) {
        // Two forms carry an IPv4 address in their last four bytes and reach it:
        // mapped ::ffff:0:0/96 (Java mostly hands those over as IPv4 already), and
        // NAT64 64:ff9b::/96, which on an IPv6-only network with DNS64 is how every
        // IPv4 host is reached - so a public one must still be fetched.
        if (startsWith(b, new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff})
                || startsWith(b, new byte[] {0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0})) {
            try {
                return isPublic(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
            } catch (UnknownHostException e) {
                return false;
            }
        }
        int first = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
        int second = ((b[2] & 0xff) << 8) | (b[3] & 0xff);
        if ((first & 0xe000) != 0x2000) {
            return false;                                  // not 2000::/3: local, multicast, IPv4-compatible...
        }
        return !(first == 0x2001 && second < 0x0200)       // 2001::/23 IETF protocol assignments, Teredo among them
                && !(first == 0x2001 && second == 0x0db8)  // 2001:db8::/32 documentation
                && first != 0x2002                         // 2002::/16 6to4, deprecated, carries an IPv4 address
                && !(first == 0x3fff && second < 0x1000)   // 3fff::/20 documentation
                && first != 0x5f00;                        // 5f00::/16 SRv6 SIDs
    }

    private static boolean startsWith(byte[] address, byte[] prefix) {
        return Arrays.equals(address, 0, prefix.length, prefix, 0, prefix.length);
    }
}

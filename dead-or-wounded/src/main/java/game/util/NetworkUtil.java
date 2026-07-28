package game.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

/** Shared helper for finding this machine's LAN-reachable IPv4 addresses. */
public final class NetworkUtil {
    private NetworkUtil() {}

    public static List<String> listLanIpv4Addresses() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(iface -> {
                        try {
                            return iface.isUp() && !iface.isLoopback() && !iface.isVirtual();
                        } catch (SocketException e) {
                            return false;
                        }
                    })
                    .flatMap(iface -> Collections.list(iface.getInetAddresses()).stream())
                    .filter(addr -> addr instanceof Inet4Address)
                    .map(InetAddress::getHostAddress)
                    .toList();
        } catch (SocketException e) {
            return List.of();
        }
    }
}

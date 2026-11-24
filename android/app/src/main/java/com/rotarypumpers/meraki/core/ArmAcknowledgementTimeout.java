package com.rotarypumpers.meraki.core;
import java.io.IOException;
/** Only the exact native ACK timeout is eligible for bounded recovery. */
public final class ArmAcknowledgementTimeout extends IOException {
 public ArmAcknowledgementTimeout(){super("Phone acknowledgement timed out");}
}

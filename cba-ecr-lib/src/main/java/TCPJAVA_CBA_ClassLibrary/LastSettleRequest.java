/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary;

/**
 * Request Last Settlement (dataType "lastSettleRequest").
 *
 * Identical to SettleRequest apart from the command name: it returns the totals
 * of the last settlement the terminal completed, so the POS can confirm whether
 * a settlement went through before retrying one.
 *
 * @author gimhan added
 */
public class LastSettleRequest extends SettleRequest {

    LastSettleRequest(String strIP, int Port, Boolean EnableEncrypt) {
        super(strIP, Port, EnableEncrypt, "lastSettleRequest");
    }

    LastSettleRequest() {
        super("lastSettleRequest");
    }
}

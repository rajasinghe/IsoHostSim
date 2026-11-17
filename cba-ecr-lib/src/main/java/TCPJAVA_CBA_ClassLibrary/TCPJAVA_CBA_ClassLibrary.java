/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary;


import TCPJAVA_CBA_ClassLibrary.Communication.PaxPort;   // ISO8583Studio patch
import TCPJAVA_CBA_ClassLibrary.Communication.USbConnector;
import TCPJAVA_CBA_ClassLibrary.Requests.CheckStatus;
import org.json.JSONObject;

/**
 *
 */
public class TCPJAVA_CBA_ClassLibrary {

    //TCP communication functions
    //TCP communication check status

    public JSONObject CheckStatus(String strIP, int Port) {
        CheckStatusRequest checkStatusRequest = new CheckStatusRequest(strIP, Port);
        JSONObject CheckStatusRequestResponse = checkStatusRequest.CheckStatus_TCP();
        return CheckStatusRequestResponse;
    }

    //TCP communication RequestBin
    public JSONObject RequestBin(String strIP, int Port, JSONObject BinRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        BinRequest binRequest = new BinRequest(strIP, Port, BinRequestJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject BinRequestResponse = binRequest.RequestBin_TCP();
        return BinRequestResponse;

    }

    //TCP communication RequestDoSale
    public JSONObject RequestDoSale(String strIP, int Port, JSONObject DoSaleRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        DoSaleRequest doSaleRequest = new DoSaleRequest(strIP, Port, DoSaleRequestJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject DoSaleRequestResponse = doSaleRequest.RequestDoSale_TCP();
        return DoSaleRequestResponse;

    }

    //TCP communication DoVoid
    public JSONObject DoVoid(String strIP, int Port, JSONObject DoVoidRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        VoidRequest voidRequest = new VoidRequest(strIP, Port, DoVoidRequestJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject VoidRequestResponse = voidRequest.DoVoid_TCP();
        return VoidRequestResponse;

    }

    //TCP communication RequestLast
    public JSONObject RequestLast(String strIP, int Port, JSONObject DoRequestLastJsonObjectCommand, Boolean EnableEncrypt, int intTo) {

        LastTranDataRequest lastTranDataRequest = new LastTranDataRequest(strIP, Port, DoRequestLastJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject LastTranDataRequestResponse = lastTranDataRequest.RequestLast_TCP();
        return LastTranDataRequestResponse;

    }

    //TCP serial communication RequestLast
    public JSONObject RequestTransferKey(String strIP, int Port, JSONObject TransferKeyJsonObjectCommand, int intTo) {
        TransferKeyRequest transferKeyRequest = new TransferKeyRequest(strIP, Port, TransferKeyJsonObjectCommand, intTo);
        JSONObject transferKeyRequestResponse = transferKeyRequest.RequestTransferKey_TCP();
        return transferKeyRequestResponse;

    }

    //USB communication functions
    //usb serial communication check status
    public JSONObject CheckStatus() {
        // ISO8583Studio patch: the original went through USbConnector, whose serialPort field is
        // never assigned, so every USB Check Status failed with NAK 17 before anything was sent.
        // CheckStatusRequest.CheckStatus_USB() is the library's own working implementation on the
        // shared PaxPort, the same path every other USB command uses.
        CheckStatusRequest checkStatusRequest = new CheckStatusRequest();
        return checkStatusRequest.CheckStatus_USB();
    }

    //usb serial communication RequestBin
    public JSONObject RequestBin(JSONObject BinRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        BinRequest binRequest = new BinRequest(BinRequestJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject binRequestResponse = binRequest.RequestBin_USB();
        return binRequestResponse;
    }

    //usb serial communication RequestDoSale
    public JSONObject RequestDoSale(JSONObject DoSaleRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        DoSaleRequest doSaleRequest = new DoSaleRequest(DoSaleRequestJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject doSaleRequestResponse = doSaleRequest.RequestDoSale_USB();
        return doSaleRequestResponse;
    }

    //usb serial communication DoVoid
    public JSONObject DoVoid(JSONObject DoVoidRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        VoidRequest voidRequest = new VoidRequest(DoVoidRequestJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject voidRequestResponse = voidRequest.DoVoid_USB();
        return voidRequestResponse;

    }

    //usb serial communication RequestLast
    public JSONObject RequestLast(JSONObject DoRequestLastJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        LastTranDataRequest lastTranDataRequest = new LastTranDataRequest(DoRequestLastJsonObjectCommand, EnableEncrypt, intTo);
        JSONObject LastTranDataRequestResponse = lastTranDataRequest.RequestLast_USB();
        return LastTranDataRequestResponse;

    }

    //usb serial communication RequestLast
    public JSONObject RequestTransferKey(JSONObject TransferKeyJsonObjectCommand, int intTo) {
        TransferKeyRequest transferKeyRequest = new TransferKeyRequest(TransferKeyJsonObjectCommand, intTo);
        JSONObject TransferKeyRequestResponse = transferKeyRequest.RequestTransferKey_USB();
        return TransferKeyRequestResponse;
    }

    // Settlement, Request Last Settlement and Transaction Cancel
    //TCP communication settlement
    public JSONObject RequestDoSettlement(String strIP, int Port, Boolean EnableEncrypt) {
        SettleRequest settleRequest = new SettleRequest(strIP, Port, EnableEncrypt);
        JSONObject SettleRequestResponse = settleRequest.Settle_TCP();
        return SettleRequestResponse;
    }

    //usb serial communication settlement
    public JSONObject RequestDoSettlement() {
        SettleRequest settleRequest = new SettleRequest();
        JSONObject SettleRequestResponse = settleRequest.Settle_USB();
        return SettleRequestResponse;
    }

    //TCP communication request last settlement
    public JSONObject RequestLastSettlement(String strIP, int Port, Boolean EnableEncrypt) {
        LastSettleRequest lastSettleRequest = new LastSettleRequest(strIP, Port, EnableEncrypt);
        JSONObject LastSettleRequestResponse = lastSettleRequest.Settle_TCP();
        return LastSettleRequestResponse;
    }

    //usb serial communication request last settlement
    public JSONObject RequestLastSettlement() {
        LastSettleRequest lastSettleRequest = new LastSettleRequest();
        JSONObject LastSettleRequestResponse = lastSettleRequest.Settle_USB();
        return LastSettleRequestResponse;
    }

    //TCP communication transaction cancel - call from a different thread from the transaction
    public JSONObject RequestTransactionCancel(String strIP, int Port) {
        TranCancelRequest tranCancelRequest = new TranCancelRequest(strIP, Port);
        JSONObject TranCancelRequestResponse = tranCancelRequest.TranCancel_TCP();
        return TranCancelRequestResponse;
    }

    //usb serial communication transaction cancel - call from a different thread from the transaction
    public JSONObject RequestTransactionCancel() {
        TranCancelRequest tranCancelRequest = new TranCancelRequest();
        JSONObject TranCancelRequestResponse = tranCancelRequest.TranCancel_USB();
        return TranCancelRequestResponse;
    }

    // ISO8583Studio patch: close the shared PAX USB port (PaxPort keeps it open between commands).
    public static void closeUsb() {
        PaxPort.shutdown();
    }

}

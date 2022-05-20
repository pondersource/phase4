# Peppol sender receiver simulation
## This is just to test our php implementation of peppol

It does not check the validity of the certificates and comes with "hardcoded" keys.
If you wish to implement AS4 or peppol, feel free to use this for testing

Full documentation is available at the main phase4 repository [here](https://github.com/phax/phase4).

## Setup and configurations

### General steps
Use JRE 1.8 everywhere!

### Setting up the receiver/server
Designate a folder as the working directory to run the receiver/server. It can be any folder, but make sure you configure JRE correctly to assume that folder as the working directory. 
1. Generate a keystore that the receiver will use to read its messages with:
    ```
    keytool.exe -genkeypair -keystore test.p12 -storetype PKCS12 -storepass peppol -alias "pondersource" -keyalg RSA -keysize 2048 -validity 99999 -dname "CN=My SSL Certificate, OU=My Team, O=My Company, L=My City, ST=My State, C=SA" -ext san=dns:nimladris,dns:localhost,ip:127.0.0.1
    ```
2. Copy the generated keystore file (`test.p12`) into the working directory under a folder named `keys`.
3. Run the receiver/server by running the test code under the module `phase4-peppol-server-webapp` class `RunInJettyPHASE4PEPPOL`.
4. The receiver/server will be listening to port 8080 on localhost. Open `http://localhost:8080` to check the status. The end point that receives the messages is `http://localhost:8080/as4`.

### Setting up the sender/client
Designate a folder as the working directory to run the sender/client. It can be any folder, but make sure you configure JRE correctly to assume that folder as the working directory.
1. Generate a certificate for the receiver/server's keystore file (`test.p12`) to sign the messages with:
    ```
    keytool -exportcert -alias pondersource -keystore test.p12 -file certificate.cer
    ```
2. Copy the generated certificate file (`certificate.cer`) into the working directory.
3. Generate a keystore that the sender will use to sign its messages with:
    ```
    keytool -genkeypair -keystore test-ap-2021.p12 -storetype PKCS12 -storepass peppol -alias "openpeppol aisbl id von pop000306" -keyalg RSA -keysize 2048 -validity 99999 -dname "CN=My SSL Certificate, OU=My Team, O=My Company, L=My City, ST=My State, C=SA" -ext san=dns:nimladris,dns:localhost,ip:127.0.0.1
    ```
4. Copy the generated keystore file (`test-ap-2021.p12`) into the working directory.
5. Run the sender/client by running the test code under the module `phase4-peppol-client` class `MainPhase4PeppolSender`.
6. The sender/client will send a message to localhost on port 8080. The end point it will try to talk to is `http://localhost:8080/as4`.

### Configuration
In progress...

## Changes to the main repository

### Sender
``` Java
com.helger.phase4.peppol.MainPhase4PeppolSender
```
*Line 71:* Receiver id is replaced to one that actually exists [here](http://smp.helger.com/public). The receiver tries to look up this identity from the SMP and if not found, it fails.

*Line 84:* validationConfiguration is commented out. It seemed suspicious! Never tried to run the code with it!

``` Java
com.helger.phase4.sender.AbstractAS4UserMessageBuilderMIMEPayload.mainSendMessage:116
```
Receiver end point url is replaced with local host after the SMP lookup returns.


``` Java
com.helger.phase4.peppol.Phase4PeppolSender.finishFields:598
```
Replaced the receiver certificate that is returned from the SMP, by a certificate that is loaded from the local file. The placement certificate is created using the receiver's private key.

### Receiver
``` Java
com.helger.phase4.peppol.servlet.Phase4PeppolServletMessageProcessorSPI.processAS4UserMessage:543
```
`aReceiverCheckData` is set to be always null. This disables the part of the code that checks whether the received message is actually meant for the current receiver. Because the check always fails with this error message: *Failed to resolve SMP endpoint for provided receiver ID.* Note that with this change, changing the receiver id in the sender side code is probably redundant. But I did not try changing it back.

``` Java
com.helger.phase4.servlet.soap.SOAPHeaderElementProcessorWSS4J._verifyAndDecrypt:120
```
The `RequestData` class is extended in place to always return `null` as the validator. If a validator is available, the logic will try to validate the certificate (of either the sender or the receiver I do not know) which will fail because it can not verify the certificate chain.

### Changes done by [br0kk0l1](https://github.com/br0kk0l1)
- [make phase4 assume the provided certificate is valid](https://github.com/phax/phase4/commit/6c4f6d664a2ce0ea68acce8d8a8c4c618091523b)
- [Added properties file for the receiver](https://github.com/phax/phase4/commit/034485a4a12b0efb31019bf6da13b4dcb08d4829)

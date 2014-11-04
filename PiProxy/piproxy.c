#include <stdio.h>
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include <wiringPi.h>
#include <wiringSerial.h>

#define SERIAL_DEV "/dev/ttyAMA0"
#define SERIAL_BAUDRATE 115200

#define SERVER_LISTEN_TCP_PORT 6000

// TODO
// -- Define some debug log defines in order to enable/disable those logs quickly
//      -- They should include levels (verbose, debug, info, warning, error)
//      -- They could include time measurements


void onNewClientConnected (int clientSocketFd);
void onBytesReceived(char* bytes, int size);
void onPacketReceived(int type, int payload);

// file descriptor of serial device
int serialFd;

int isLedEnabled = 0;

int main( int argc, char *argv[] )
{
    printf("INFO\t\tPi-Proxy started!\n\n\n");

    int serverSocketFd, newClientSocketFd, clilen;
    int portno = SERVER_LISTEN_TCP_PORT;

    struct sockaddr_in serv_addr, cli_addr;

    int yes = 1;

    int pid;

    if ((serialFd = serialOpen (SERIAL_DEV, SERIAL_BAUDRATE)) < 0)
    {
        fprintf (stderr, "Unable to open serial device: %s\n", strerror (1)) ;
        return 1 ;
    }
 
    if (wiringPiSetup () == -1)
    {
        printf("wiringSetup returned -1");
        exit(1);
    }

    pinMode(7, OUTPUT);

    /* First call to socket() function */
    serverSocketFd = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocketFd < 0) 
    {
        perror("ERROR opening socket");
        exit(1);
    }
    /* Initialize socket structure */
    bzero((char *) &serv_addr, sizeof(serv_addr));
    portno = 6000;
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);


    setsockopt(serverSocketFd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(int));
 
    /* Now bind the host address using bind() call.*/
    if (bind(serverSocketFd, (struct sockaddr *) &serv_addr,
                          sizeof(serv_addr)) < 0)
    {
         perror("ERROR on binding");
         exit(1);
    }
    /* Now start listening for the clients, here 
     * process will go in sleep mode and will wait 
     * for the incoming connection
     */
    listen(serverSocketFd,5);
    clilen = sizeof(cli_addr);

    while (1) 
    {
        printf("INFO\t\tListening on port %d ...\n",portno);
        
        newClientSocketFd = accept(serverSocketFd, 
                (struct sockaddr *) &cli_addr, &clilen);
        if (newClientSocketFd < 0)
        {
            perror("ERROR\t\tERROR on accept");
            //exit(1);
        }

        printf("INFO\t\tNew client with address %s connected!\n",inet_ntoa(cli_addr.sin_addr));

        /* Create child process */
        /*pid = fork();
        if (pid < 0)
        {
            perror("ERROR on fork");
        exit(1);
        }*/
        
        //if (pid == 0)  
        //{
            /* This is the client process */
        //    close(sockfd);
        onNewClientConnected(newClientSocketFd);
        close(newClientSocketFd);
        //    exit(0);
        //}
        //else
        //{
        //    close(newsockfd);
        //}
    } /* end of while */
}

void onNewClientConnected (int clientSocketFd)
{
    int n = 0;
    char buffer[256];
    bzero(buffer,256);

    while((n = read(clientSocketFd,buffer,255)) >= 0)
    {
        onBytesReceived(buffer,n);

/*      // DEBUG
        printf("stringContent: %s\n",sendBuffer);

        int i;
        for(i = 0;i < 6;i++)
        {   
            printf("byteContent[%d]: %d\n",i,(int)sendBuffer[i]);
        }
*/

        // Send back to client
        // n = write(sock,"I got your message\n",18);
        // if (n < 0) 
        // {
        //     perror("ERROR writing to socket");
        //     break;
        // }
    }

    /*if (n < 0)
    {
        perror("ERROR reading from socket");
        exit(1);
    }
    printf("Here is the message: %s\n",buffer);
    n = write(sock,"I got your message",18);
    if (n < 0) 
    {
        perror("ERROR writing to socket");
        exit(1);
    }*/
}


#define PACKET_READ_STATE_PREFIX 0
#define PACKET_READ_STATE_TYPE 1
#define PACKET_READ_STATE_PAYLOAD 2
#define PACKET_PREFIX_INT_VALUE 91
#define PACKET_SEPARATOR_INT_VALUE 61
#define PACKET_SUFFIX_INT_VALUE 93
#define DIGIT_BEGIN_ASCII_CODE 48
#define DIGIT_END_ASCII_CODE 57

//
// [33=100]
//




int packetReadState = PACKET_READ_STATE_PREFIX;
int packetReadStateContentCounter = 0;
char typeBuffer[10];
char payloadBuffer[1024];

void onBytesReceived(char* bytes, int size)
{
    // printf("DEBUG\t\tonBytesReceived(size(%d))\n",size);

    // blink led when data transfer, similar to the arduino :)
    isLedEnabled = isLedEnabled == 0 ? 1 : 0;
    digitalWrite(7,isLedEnabled);

    int i;
    for(i = 0; i < size;i++)
    {
        int byteIntValue = (int)bytes[i];
        // if char value below 33, it is a control character => ignore it
        if (byteIntValue <= 32 || byteIntValue == 127)
        {
            continue;
        }
        // printf("DEBUG\t\t\tonBytesReceived:processByte[index:%d][value:%d]\n",i,byteIntValue);
        switch(packetReadState)
        {
            case PACKET_READ_STATE_PREFIX:
                if (PACKET_PREFIX_INT_VALUE == byteIntValue)
                {
                    // printf("DEBUG\t\t\t->PACKET_PREFIX_INT_VALUE detected!\n");
                    // printf("DEBUG\t\t\t->CHANGE_TO_STATE: PACKET_READ_STATE_TYPE!\n");
                    packetReadStateContentCounter = 0;
                    packetReadState = PACKET_READ_STATE_TYPE;
                }
                break;
            case PACKET_READ_STATE_TYPE:
                if(byteIntValue >= DIGIT_BEGIN_ASCII_CODE && byteIntValue <= DIGIT_END_ASCII_CODE 
                    && packetReadStateContentCounter < 8)
                {
                    // printf("DEBUG\t\t\t->TYPE_CONTENT detected!\n");
                    typeBuffer[packetReadStateContentCounter++] = (char)byteIntValue;
                }
                else if(byteIntValue==PACKET_SEPARATOR_INT_VALUE)
                {
                    // printf("DEBUG\t\t\t->PACKET_SEPARATOR_INT_VALUE detected!\n");
                    // printf("DEBUG\t\t\t->CHANGE_TO_STATE: PACKET_READ_STATE_PAYLOAD!\n");
                    packetReadState = PACKET_READ_STATE_PAYLOAD;
                    typeBuffer[packetReadStateContentCounter++] = 0;// make null terminated string
                    packetReadStateContentCounter = 0;
                }
                else
                {
                    // printf("DEBUG\t\t\t->WRONG_BYTE_DURING_TYPE_CONTENT detected!\n");
                    // printf("DEBUG\t\t\t->CHANGE_TO_STATE: PACKET_READ_STATE_PREFIX!\n");
                    // this is a error case as we read weather a number nor 
                    // the separator letter. jump to initial packet reading (prefix).
                    packetReadStateContentCounter = 0;
                    packetReadState = PACKET_READ_STATE_PREFIX;
                }
                break;
            case PACKET_READ_STATE_PAYLOAD:
                if(byteIntValue >= DIGIT_BEGIN_ASCII_CODE && byteIntValue <= DIGIT_END_ASCII_CODE 
                    && packetReadStateContentCounter < 8)
                {
                    // printf("DEBUG\t\t\t->PAYLOAD_CONTENT detected!\n");
                    payloadBuffer[packetReadStateContentCounter++] = (char)byteIntValue;
                }
                else if(byteIntValue==PACKET_SUFFIX_INT_VALUE)
                {
                    // printf("DEBUG\t\t\t->PACKET_SUFFIX_INT_VALUE detected!\n");
                    // printf("DEBUG\t\t\t->CHANGE_TO_STATE: PACKET_READ_STATE_PREFIX!\n");
                    packetReadState = PACKET_READ_STATE_PREFIX;
                    payloadBuffer[packetReadStateContentCounter++] = 0;// make null terminated string
                    packetReadStateContentCounter = 0;


                    // printf("DEBUG\t\t\t->TYPE_BUFFER=%s!\n", typeBuffer);
                    // printf("DEBUG\t\t\t->PAYLOAD_BUFFER=%s!\n", payloadBuffer);

                    // trigger new packet received
                    onPacketReceived(atoi(typeBuffer),atoi(payloadBuffer));
                }
                else
                {
                    // printf("DEBUG\t\t\t->WRONG_BYTE_DURING_PAYLOAD_CONTENT detected!\n");
                    // printf("DEBUG\t\t\t->CHANGE_TO_STATE: PACKET_READ_STATE_PREFIX!\n");
                    // this is a error case as we read weather a number nor 
                    // the separator letter. jump to initial packet reading (prefix).
                    packetReadStateContentCounter = 0;
                    packetReadState = PACKET_READ_STATE_PREFIX;
                }
                break;
        }
    }
}

// --
// common command index values
#define ARDUINO_RC_COMMAND_LENGTH 7
#define ARDUINO_RC_COMMAND_TYPE_INDEX 1
#define ARDUINO_RC_COMMAND_BEGIN '['
#define ARDUINO_RC_COMMAND_END ']'

// ---
// control command byte index values
#define ARDUINO_RC_CONTROL_TYPE_VALUE '!'
#define ARDUINO_RC_CONTROL_THROTTLE 2
#define ARDUINO_RC_CONTROL_YAW 3
#define ARDUINO_RC_CONTROL_PITCH 4
#define ARDUINO_RC_CONTROL_ROLL 5

#define PACKET_TYPE_THROTTLE 32;
#define PACKET_TYPE_YAW 33;
#define PACKET_TYPE_PITCH 34;
#define PACKET_TYPE_ROLL 35;

private static final int BUFFER_SIZE = 1024;// bytes
private byte[] mBuf = new byte[BUFFER_SIZE];
private int mWritePos = 0;

void onBytesReceived(char* bytes, int size)
{
    //        String receivedBytes = "";
//        for (int i = 0; i < newData.length; i++) {
//            receivedBytes += "(" + ((int) newData[i]) + ")";
//        }
//        Log.i("RCDataInterpreter", "RECEIVED_BYTES [" + receivedBytes + "]");

        /*
         * Add received bytes to our buffer
         */
        writeIntoBuffer(bytes, size);

//        Log.i("RCDataInterpreter", "    availableBytes: " + mWritePos);

        int numProbeBytes = mWritePos - (ARDUINO_RC_COMMAND_LENGTH - 1);

//            Log.i("RCDataInterpreter", "    additionalBytes: " + additionalBytes);

        int i = 0;
        while (i < numProbeBytes) {
//                Log.i("RCDataInterpreter", "    checkOnPos: mBuf[" + i + "] = " + mBuf[i] + ", mBuf[" + i + 7 + "] = " + mBuf[i + 7] + ";");
            if (mBuf[i] == ARDUINO_RC_COMMAND_BEGIN && mBuf[i + (ARDUINO_RC_COMMAND_LENGTH - 1)] == ARDUINO_RC_COMMAND_END) {
                char commandType = mBuf[i + ARDUINO_RC_COMMAND_TYPE_INDEX];
                switch (commandType) {
                    case ARDUINO_RC_CONTROL_TYPE_VALUE:// ascii dec val 33
                        char throttle = mBuf[i + ARDUINO_RC_CONTROL_THROTTLE];
                        char yaw = mBuf[i + ARDUINO_RC_CONTROL_YAW];
                        char pitch = mBuf[i + ARDUINO_RC_CONTROL_PITCH];
                        char roll = mBuf[i + ARDUINO_RC_CONTROL_ROLL];

                        // these conversion are required in java as java bytes
                        // are always signed and we need values higher then 127, which
                        // int supports :)
                        int throttleInValue = (int) throttle & 0xFF;
                        int yawInValue = (int) yaw & 0xFF;
                        int pitchInValue = (int) pitch & 0xFF;
                        int rollInValue = (int) roll & 0xFF;

                        onPacketReceived(PACKET_TYPE_THROTTLE, throttleInValue);
                        onPacketReceived(PACKET_TYPE_YAW, yawInValue);
                        onPacketReceived(PACKET_TYPE_PITCH, pitchInValue);
                        onPacketReceived(PACKET_TYPE_ROLL, rollInValue);

                        break;
                }
                i += ARDUINO_RC_COMMAND_LENGTH;
            } else {
                i++;
            }
        }

//            Log.i("RCDataInterpreter", "    numCheckBytes: " + i);
        // if at least of amount of bytes has been probed, remove the written bytes
        if (i > 0) {
            mWritePos -= i;
            if (mWritePos != 0) {
                memcpy();
                System.arraycopy(mBuf, i, mBuf, 0, mWritePos);
            }
        }
}

int writeIntoBuffer(char* bytes, int length)
{
    if (length > 0) {
        int newSize = mWritePos + length;
        if (newSize > BUFFER_SIZE) {
            return -1;
        }
        if (bytes == 0x0) {
            return -2;
        }
        if (length <= 0) {
            return -3;
        }
        memcpy(mBuf + mWritePos, bytes, length);
        mWritePos = newSize;
    }
}

void onPacketReceived(int type, int payload)
{
    printf("INFO\t\tonPacketReceived[type=%d][payload=%d]\n",type,payload);
    
    char sendBuffer[6];

    // set first byte to '>'
    sendBuffer[0] = 62;

    // set command type bytes
    sendBuffer[1] = type & 0xFF;
    sendBuffer[2] = (type >> 8) & 0xFF;

    // set command payload bytes
    sendBuffer[3] = payload & 0xFF;
    sendBuffer[4] = (payload >> 8) & 0xFF;

    // set last byte to '<'
    sendBuffer[5] = 60;

    write(serialFd, sendBuffer, 6) ;
}








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

#define PACKET_TYPE_THROTTLE 32
#define PACKET_TYPE_YAW 33
#define PACKET_TYPE_PITCH 34
#define PACKET_TYPE_ROLL 35

#define BUFFER_SIZE 1024// bytes
char mBuf[BUFFER_SIZE];
int mWritePos = 0;

char throttle;
char yaw;
char pitch;
char roll;

int throttleInValue;
int yawInValue;
int pitchInValue;
int rollInValue;

char commandType;
int i;
int numProbeBytes;
int writeRes;


int lastThrottlePacketValue = -1;
int lastYawPacketValue = -1;
int lastPitchPacketValue = -1;
int lastRollPacketValue = -1;

int valueDiff = 0;

int transmitCounter = 0;
int transmitNum = 100;


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
        writeRes = writeIntoBuffer(bytes, size);

        if( writeRes != 0 )
        {
            perror("ERROR writing to receive buffer");
            return;
        }

//        Log.i("RCDataInterpreter", "    availableBytes: " + mWritePos);

        numProbeBytes = mWritePos - (ARDUINO_RC_COMMAND_LENGTH - 1);

//            Log.i("RCDataInterpreter", "    additionalBytes: " + additionalBytes);

        i = 0;
        while (i < numProbeBytes) {
//                Log.i("RCDataInterpreter", "    checkOnPos: mBuf[" + i + "] = " + mBuf[i] + ", mBuf[" + i + 7 + "] = " + mBuf[i + 7] + ";");
            if (mBuf[i] == ARDUINO_RC_COMMAND_BEGIN && mBuf[i + (ARDUINO_RC_COMMAND_LENGTH - 1)] == ARDUINO_RC_COMMAND_END) {
                commandType = mBuf[i + ARDUINO_RC_COMMAND_TYPE_INDEX];
                switch (commandType) {
                    case ARDUINO_RC_CONTROL_TYPE_VALUE:// ascii dec val 33
                        throttle = mBuf[i + ARDUINO_RC_CONTROL_THROTTLE];
                        yaw = mBuf[i + ARDUINO_RC_CONTROL_YAW];
                        pitch = mBuf[i + ARDUINO_RC_CONTROL_PITCH];
                        roll = mBuf[i + ARDUINO_RC_CONTROL_ROLL];

                        // these conversion are required in java as java bytes
                        // are always signed and we need values higher then 127, which
                        // int supports :)
                        throttleInValue = (int) throttle & 0xFF;
                        yawInValue = (int) yaw & 0xFF;
                        pitchInValue = (int) pitch & 0xFF;
                        rollInValue = (int) roll & 0xFF;
                        //transmitCounter++;
                        //if(transmitCounter==transmitNum)
                        //{
                            transmitCounter = 0;
                            valueDiff = (throttleInValue - lastThrottlePacketValue);
                            if(valueDiff != 0)
                            {
                                lastThrottlePacketValue = throttleInValue;
                                onPacketReceived(PACKET_TYPE_THROTTLE, throttleInValue);
                            }

                            valueDiff += (yawInValue - lastYawPacketValue);
                            if(valueDiff != 0)
                            {
                                lastYawPacketValue = yawInValue;
                                onPacketReceived(PACKET_TYPE_YAW, yawInValue);
                            }

                            valueDiff += (pitchInValue - lastPitchPacketValue);
                            if(valueDiff != 0)
                            {
                                lastPitchPacketValue = pitchInValue;
                                onPacketReceived(PACKET_TYPE_PITCH, pitchInValue);
                            }

                            valueDiff += (rollInValue - lastRollPacketValue);
                            if(valueDiff != 0)
                            {
                                lastRollPacketValue = rollInValue;
                                onPacketReceived(PACKET_TYPE_ROLL, rollInValue);
                            }
                        //}

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
                memcpy(mBuf,mBuf+i,mWritePos);
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
    return 0;
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








/*
  AnalogReadSerial
  Reads an analog input on pin 0, prints the result to the serial monitor.
  Attach the center pin of a potentiometer to pin A0, and the outside pins to +5V and ground.

 This example code is in the public domain.
 */


#define NUM_CHANNELS 4
#define PIN_YAW A0
#define PIN_THROTTLE A2
#define PIN_PITCH A4
#define PIN_ROLL A6

#define THROTTLE_MIN_CALIBRATION_VALUE   580
#define THROTTLE_MAX_CALIBRATION_VALUE   290
#define YAW_MIN_CALIBRATION_VALUE        588
#define YAW_MAX_CALIBRATION_VALUE        285
#define PITCH_MIN_CALIBRATION_VALUE      345
#define PITCH_MAX_CALIBRATION_VALUE      620
#define ROLL_MIN_CALIBRATION_VALUE       650
#define ROLL_MAX_CALIBRATION_VALUE       350
#define CONTROL_MIN_OUT_VALUE            0
#define CONTROL_MAX_OUT_VALUE            255

/*
char strBuffer[100];
const byte ANALOG_PINS[] = {PIN_YAW,PIN_THROTTLE,PIN_PITCH,PIN_ROLL};

#define VALUE_HISTORY_SIZE 10
int yawValueHistory[VALUE_HISTORY_SIZE];
int throttleValueHistory[VALUE_HISTORY_SIZE];
int pitchValueHistory[VALUE_HISTORY_SIZE];
int rollValueHistory[VALUE_HISTORY_SIZE];

int yawTotal = 0;
int throttleTotal = 0;
int pitchTotal = 0;
int rollTotal = 0;

int yawAverageOld = 0;
int throttleAverageOld = 0;
int pitchAverageOld = 0;
int rollAverageOld = 0;

int index = 0;
*/

int throttleValue = CONTROL_MIN_OUT_VALUE;
int yawValue = CONTROL_MIN_OUT_VALUE;
int pitchValue = CONTROL_MIN_OUT_VALUE;
int rollValue = CONTROL_MIN_OUT_VALUE;

const byte COMMAND_LENGTH = 7;
byte commandByteArray[] = {'[', '!', 0, 0, 0, 0, ']'};

// the setup routine runs once when you press reset:
void setup() {
  // initialize serial communication at 9600 bits per second:
  Serial.begin(9600);

  /*
  for(int i = 0; i < VALUE_HISTORY_SIZE;i++)
  {
    yawValueHistory[i] = 0;
    throttleValueHistory[i] = 0;
    pitchValueHistory[i] = 0;
    rollValueHistory[i] = 0;
  }
  */
}

// the loop routine runs over and over again forever:
void loop()
{
  /*yawTotal -= yawValueHistory[index];
  yawValueHistory[index] = analogRead(PIN_YAW);
  yawTotal += yawValueHistory[index];

  throttleTotal -= throttleValueHistory[index];
  throttleValueHistory[index] = analogRead(PIN_THROTTLE);
  throttleTotal += throttleValueHistory[index];

  pitchTotal -= pitchValueHistory[index];
  pitchValueHistory[index] = analogRead(PIN_PITCH);
  pitchTotal += pitchValueHistory[index];

  rollTotal -= rollValueHistory[index];
  rollValueHistory[index] = analogRead(PIN_ROLL);
  rollTotal += rollValueHistory[index];



  int average = yawTotal / VALUE_HISTORY_SIZE;
  if(average != yawAverageOld)
  {
      sprintf(strBuffer, "[YAW=%d]",average);
      Serial.println(strBuffer);
  }
  yawAverageOld = average;

  average = throttleTotal / VALUE_HISTORY_SIZE;
  if(average != throttleAverageOld)
  {
      sprintf(strBuffer, "[THROTTLE=%d]",average);
      Serial.println(strBuffer);
  }
  throttleAverageOld = average;

  average = pitchTotal / VALUE_HISTORY_SIZE;
  if(average != pitchAverageOld)
  {
      sprintf(strBuffer, "[PITCH=%d]",average);
      Serial.println(strBuffer);
  }
  pitchAverageOld = average;

  average = rollTotal / VALUE_HISTORY_SIZE;
  if(average != rollAverageOld)
  {
      sprintf(strBuffer, "[ROLL=%d]",average);
      Serial.println(strBuffer);
  }
  rollAverageOld = average;

  index++;
  if(index >= VALUE_HISTORY_SIZE)
  {
    index = 0;
  }*/


  /*sprintf(strBuffer, "[%d=%d]",i,sensorValue);
  Serial.println(strBuffer);

  for(int i = 0;i < NUM_CHANNELS;i++)
  {
    sensorValue = analogRead(ANALOG_PINS[i]);
    if(sensorValue != lastSensorValues[i])
    {
      sprintf(strBuffer, "[%d=%d]",i,sensorValue);
      Serial.println(strBuffer);
    }
    lastSensorValues[i] = sensorValue;
  }*/

  throttleValue = analogRead(PIN_THROTTLE);
  yawValue = analogRead(PIN_YAW);
  pitchValue = analogRead(PIN_PITCH);
  rollValue = analogRead(PIN_ROLL);

  throttleValue  = map(throttleValue,  THROTTLE_MIN_CALIBRATION_VALUE,  THROTTLE_MAX_CALIBRATION_VALUE,  CONTROL_MIN_OUT_VALUE,  CONTROL_MAX_OUT_VALUE);
  yawValue       = map(yawValue,       YAW_MIN_CALIBRATION_VALUE,       YAW_MAX_CALIBRATION_VALUE,       CONTROL_MIN_OUT_VALUE,  CONTROL_MAX_OUT_VALUE);
  pitchValue     = map(pitchValue,     PITCH_MIN_CALIBRATION_VALUE,     PITCH_MAX_CALIBRATION_VALUE,     CONTROL_MIN_OUT_VALUE,  CONTROL_MAX_OUT_VALUE);
  rollValue      = map(rollValue,      ROLL_MIN_CALIBRATION_VALUE,      ROLL_MAX_CALIBRATION_VALUE,      CONTROL_MIN_OUT_VALUE,  CONTROL_MAX_OUT_VALUE);

  commandByteArray[2] = max( CONTROL_MIN_OUT_VALUE, min(CONTROL_MAX_OUT_VALUE, throttleValue));
  commandByteArray[3] = max( CONTROL_MIN_OUT_VALUE, min(CONTROL_MAX_OUT_VALUE, yawValue));
  commandByteArray[4] = max( CONTROL_MIN_OUT_VALUE, min(CONTROL_MAX_OUT_VALUE, pitchValue));
  commandByteArray[5] = max( CONTROL_MIN_OUT_VALUE, min(CONTROL_MAX_OUT_VALUE, rollValue));

  // write command byte array to serial
  Serial.write(commandByteArray, COMMAND_LENGTH);

  delay(1);        // delay in between reads for stability
}

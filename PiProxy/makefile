all: piproxy

piproxy: piproxy.o
	gcc -lwiringPi -lpthread piproxy.o -o piproxy

piproxy.o: piproxy.c
	gcc -c piproxy.c

clean:
	rm -rf *o piproxy

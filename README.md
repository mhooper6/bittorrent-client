bittorrent-client
=================

A project I did for my Computer Networking I class at Georgia Tech in spring of 2013.

This is not a fully functional bittorrent client; the purpose of the project was to learn the basics of the bittorrent protocol. What it does is find peers in a torrent, perform the handshake part of the protocol, and then begin requesting pieces once the peer sends the unchoke message. My client uses the tracker and bencoder from the [bitlet](https://github.com/bitletorg/bitlet) library.

As I mentioned, it's not fully functional but it was a fun project that I learned a lot from. Hopefully one of these days I'll have time to finish it.

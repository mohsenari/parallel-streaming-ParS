# parallel-streaming-ParS

To stream video using HTTP, a client device sequentially requests and receives chunks of the video file from the server over a TCP connection. It is well-known that TCP performs poorly in networks with high latency and packet loss such as wireless networks. On mobile devices, in particular, using a single TCP connection for video streaming is not efficient, and thus, the user may not receive the highest video quality possible. In this work, we implemented a system called ParS that uses parallel TCP connections to stream video on mobile devices. Our system uses parallel connections to fetch each chunk of the video file using HTTP range requests. 

This work isn't finilized yet and needs additional modules to be usable for video streaming. So far we have focused on developing the idea and the new method. We will add video player and customizable server settings in future.

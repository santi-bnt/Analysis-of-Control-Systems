G = tf([1.975],[1 13.34 7.063])

rlocus(G)
[k,poles]=rlocfind(G)
h = feedback(G,k)
step(h)
final = 1.975/44.49
kr = 1/final
step (kr*h)
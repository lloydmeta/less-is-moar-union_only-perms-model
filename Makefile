ES_PASSWORD:=changemechang3mechangeme

start-es:
	docker network create elastic
	docker run --name es --net elastic -p 9200:9200 -it --env discovery.type=single-node --env ELASTIC_PASSWORD=${ES_PASSWORD} -m 1GB docker.elastic.co/elasticsearch/elasticsearch:8.11.4

stop-es:
	docker rm -f es
	docker network rm elastic

repl:
	ES_PASSWORD=${ES_PASSWORD} amm --watch --predef poc.sc
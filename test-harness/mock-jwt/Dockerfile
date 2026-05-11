FROM python:3.12-alpine
WORKDIR /app
RUN pip install --no-cache-dir flask cryptography pyjwt
COPY mock_oidc.py .
EXPOSE 8090
CMD ["python", "-u", "mock_oidc.py"]

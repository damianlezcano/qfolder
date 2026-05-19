upload_asset() {
	local release_id="$1" file="$2" name="$3"
	if [ -f "$file" ]; then
		local size=$(du -h "$file" | cut -f1)
		ok "  Uploading $name ($size) ..."
		local http_code=$(curl --connect-timeout 30 --max-time 900 -sS -w "%{http_code}" \
			-X POST "$API/releases/$release_id/assets?name=$name" \
			"${CURL_AUTH[@]}" -H "Content-Type: application/octet-stream" \
			--data-binary @"$file" -o /tmp/qfolder-upload-response.txt)
		if [ "$http_code" = "201" ] || [ "$http_code" = "422" ]; then
			ok "  $name  OK (HTTP $http_code)"
		else
			warn "  $name  FAILED (HTTP $http_code)"
			head -3 /tmp/qfolder-upload-response.txt 2>/dev/null || true
		fi
	fi
}

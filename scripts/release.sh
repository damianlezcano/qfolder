upload_asset() {
	local release_id="$1" file="$2" name="$3"
	if [ -f "$file" ]; then
		local size=$(du -h "$file" | cut -f1)
		ok "  Uploading $name ($size) ..."
		# 30 min timeout for large files
		local http_code=$(curl --connect-timeout 30 --max-time 1800 -sS -w "%{http_code}" \
			-X POST "https://uploads.github.com/repos/$REPO/releases/$release_id/assets?name=$name" \
			"${CURL_AUTH[@]}" -H "Content-Type: application/octet-stream" \
			--data-binary @"$file" -o /dev/null 2>/tmp/qfolder-upload-err.txt)
		if [ "$http_code" = "201" ] || [ "$http_code" = "422" ]; then
			ok "  $name  OK"
		elif [ "$http_code" = "000" ]; then
			warn "  $name  TIMEOUT (network too slow for $size). Upload manually at:"
			warn "    https://github.com/$REPO/releases/tag/$VERSION"
		else
			warn "  $name  FAILED (HTTP $http_code)"
		fi
	fi
}

package com.abizer_r.quickedit.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.utils.defaultTextColor

@Preview
@Composable
fun AppIconWithName(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            modifier = Modifier.size(128.dp),
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = stringResource(R.string.app_logo)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.app_name_full),
            style = MaterialTheme.typography.titleMedium.copy(
                color = defaultTextColor()
            )
        )
    }
}